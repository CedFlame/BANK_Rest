package com.example.bankcards.service;

import com.example.bankcards.config.properties.TransfersProperties;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.TransferStatus;
import com.example.bankcards.exception.*;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.mapper.TransferMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.impl.TransferServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock TransfersProperties props;
    @Mock TransferRepository transferRepository;
    @Mock CardRepository cardRepository;
    @Mock UserRepository userRepository;
    @Mock EntityManager entityManager;
    @Mock Clock clock;

    @InjectMocks
    TransferServiceImpl service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-08-25T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime NOW = LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone());

    @BeforeEach
    void setup() {
        lenient().when(clock.instant()).thenReturn(fixedClock.instant());
        lenient().when(clock.getZone()).thenReturn(fixedClock.getZone());
        lenient().when(props.getDefaultPageSize()).thenReturn(20);
        lenient().when(props.getMaxPageSize()).thenReturn(50);
        lenient().when(props.getMaxTtlSeconds()).thenReturn(0);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        return u;
    }

    private static Card card(Long id, Long ownerId, long balance, CardStatus status, YearMonth expiry, String last4) {
        Card c = new Card();
        c.setId(id);
        c.setUser(user(ownerId));
        c.setBalance(balance);
        c.setStatus(status);
        c.setExpiry(expiry);
        c.setPanLast4(last4);
        return c;
    }

    private static TransferRequest req(Long fromId, Long toId, long amount, Integer ttl, String idem) {
        TransferRequest r = new TransferRequest();
        r.setFromCardId(fromId);
        r.setToCardId(toId);
        r.setAmount(amount);
        r.setTtlSeconds(ttl);
        r.setIdempotencyKey(idem);
        return r;
    }

    private static Transfer transfer(Long id, User initiator, Card from, Card to, long amount, TransferStatus status) {
        Transfer t = new Transfer();
        t.setId(id);
        t.setInitiator(initiator);
        t.setFromCard(from);
        t.setToCard(to);
        t.setAmount(amount);
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("initiate: мгновенное исполнение (ttl null/0) -> COMPLETED, списание/зачисление, save transfer+cards")
    void initiate_immediateExecution() {
        Long me = 10L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card from = card(1L, me, 1_000, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(1)), "1111");
        Card to   = card(2L, me,   100, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "2222");

        when(entityManager.find(Card.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setId(77L);
            return t;
        });

        TransferDto dto = new TransferDto();
        try (MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {
            tm.when(() -> TransferMapper.toDto(any(Transfer.class))).thenReturn(dto);

            TransferDto out = service.initiate(me, req(1L, 2L, 300, null, null));
            assertThat(out).isSameAs(dto);

            assertThat(from.getBalance()).isEqualTo(1_000 - 300);
            assertThat(to.getBalance()).isEqualTo(100 + 300);

            ArgumentCaptor<Transfer> tCap = ArgumentCaptor.forClass(Transfer.class);
            verify(transferRepository).save(tCap.capture());
            Transfer persisted = tCap.getValue();
            assertThat(persisted.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(persisted.getExecutedAt()).isEqualTo(NOW);
            assertThat(persisted.getExpiresAt()).isNull();

            verify(cardRepository).save(from);
            verify(cardRepository).save(to);
        }
    }

    @Test
    @DisplayName("initiate: отложенное исполнение (ttl>now) -> PENDING, без движения балансов")
    void initiate_pending() {
        Long me = 11L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card from = card(3L, me, 500, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(6)), "3333");
        Card to   = card(4L, me, 700, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(6)), "4444");
        when(entityManager.find(Card.class, 3L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 4L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        try (MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {
            tm.when(() -> TransferMapper.toDto(any(Transfer.class))).thenReturn(new TransferDto());

            service.initiate(me, req(3L, 4L, 200, 3600, null));

            ArgumentCaptor<Transfer> tCap = ArgumentCaptor.forClass(Transfer.class);
            verify(transferRepository).save(tCap.capture());
            Transfer persisted = tCap.getValue();
            assertThat(persisted.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(persisted.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));

            assertThat(from.getBalance()).isEqualTo(500);
            assertThat(to.getBalance()).isEqualTo(700);
        }
    }

    @Test
    @DisplayName("initiate: повтор по idemKey с теми же параметрами -> вернуть найденный перевод")
    void initiate_idempotentSame() {
        Long me = 12L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card from = card(5L, me, 1_000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "5555");
        Card to   = card(6L, me,   200, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "6666");

        Transfer existed = transfer(100L, user(me), from, to, 150, TransferStatus.PENDING);
        when(transferRepository.findByInitiator_IdAndIdempotencyKey(me, "idem-1"))
                .thenReturn(Optional.of(existed));

        TransferDto dto = new TransferDto();
        try (MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {
            tm.when(() -> TransferMapper.toDto(existed)).thenReturn(dto);

            TransferDto out = service.initiate(me, req(5L, 6L, 150, 60, "idem-1"));
            assertThat(out).isSameAs(dto);

            verifyNoInteractions(entityManager);
            verifyNoMoreInteractions(transferRepository);
        }
    }

    @Test
    @DisplayName("initiate: idemKey конфликт по параметрам -> IdempotencyConflictException")
    void initiate_idempotentConflict() {
        Long me = 13L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card from = card(7L, me, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "7777");
        Card to   = card(8L, me,  200, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "8888");

        Transfer existed = transfer(101L, user(me), from, to, 999, TransferStatus.PENDING);
        when(transferRepository.findByInitiator_IdAndIdempotencyKey(me, "dup"))
                .thenReturn(Optional.of(existed));

        assertThatThrownBy(() -> service.initiate(me, req(7L, 8L, 150, 60, "dup")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("initiate: гонка по idemKey (save кидает DataIntegrityViolation) -> подобрать существующий")
    void initiate_idempotencyRace() {
        Long me = 14L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card from = card(9L, me, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "9999");
        Card to   = card(10L, me, 500, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "0000");
        when(entityManager.find(Card.class, 9L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        when(transferRepository.save(any(Transfer.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        Transfer existed = transfer(202L, user(me), from, to, 250, TransferStatus.PENDING);

        when(transferRepository.findByInitiator_IdAndIdempotencyKey(me, "race"))
                .thenReturn(Optional.empty(), Optional.of(existed));

        try (MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {
            TransferDto dto = new TransferDto();
            tm.when(() -> TransferMapper.toDto(existed)).thenReturn(dto);

            TransferDto out = service.initiate(me, req(9L, 10L, 250, 120, "race"));
            assertThat(out).isSameAs(dto);
        }
    }

    @Test
    @DisplayName("initiate: разные owners карт -> OwnershipViolationException")
    void initiate_differentOwners() {
        Long me = 20L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card a = card(11L, me,  1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "1111");
        Card b = card(12L, 99L, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "2222");
        when(entityManager.find(Card.class, 11L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);
        when(entityManager.find(Card.class, 12L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(b);

        assertThatThrownBy(() -> service.initiate(me, req(11L, 12L, 10, 0, null)))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    @DisplayName("initiate: карты не принадлежат текущему пользователю -> OwnershipViolationException")
    void initiate_notMyCards() {
        Long me = 21L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card a = card(13L, 77L, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "3333");
        Card b = card(14L, 77L, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusYears(1)), "4444");
        when(entityManager.find(Card.class, 13L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);
        when(entityManager.find(Card.class, 14L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(b);

        assertThatThrownBy(() -> service.initiate(me, req(13L, 14L, 10, 0, null)))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    @DisplayName("initiate: карта не ACTIVE -> InvalidCardStateException")
    void initiate_invalidCardState() {
        Long me = 22L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card a = card(15L, me, 1000, CardStatus.BLOCKED, YearMonth.from(NOW.plusYears(1)), "5555");
        Card b = card(16L, me, 1000, CardStatus.ACTIVE,  YearMonth.from(NOW.plusYears(1)), "6666");
        when(entityManager.find(Card.class, 15L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);
        when(entityManager.find(Card.class, 16L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(b);

        assertThatThrownBy(() -> service.initiate(me, req(15L, 16L, 10, 0, null)))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    @DisplayName("initiate: карта срок истёк -> CardExpiredException")
    void initiate_cardExpired() {
        Long me = 23L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card a = card(17L, me, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.minusMonths(1)), "7777");
        Card b = card(18L, me, 1000, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(1)), "8888");
        when(entityManager.find(Card.class, 17L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);
        when(entityManager.find(Card.class, 18L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(b);

        assertThatThrownBy(() -> service.initiate(me, req(17L, 18L, 10, 0, null)))
                .isInstanceOf(CardExpiredException.class);
    }

    @Test
    @DisplayName("initiate: не хватает средств -> InsufficientFundsException")
    void initiate_insufficientFunds() {
        Long me = 24L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));

        Card a = card(19L, me, 9, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(1)), "9999");
        Card b = card(20L, me, 0, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(1)), "0000");
        when(entityManager.find(Card.class, 19L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);
        when(entityManager.find(Card.class, 20L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(b);

        assertThatThrownBy(() -> service.initiate(me, req(19L, 20L, 10, 0, null)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("initiate: одинаковые from/to -> BadRequestException")
    void initiate_sameCard() {
        Long me = 25L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));
        Card a = card(21L, me, 100, CardStatus.ACTIVE, YearMonth.from(NOW.plusMonths(1)), "1111");
        when(entityManager.find(Card.class, 21L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(a);

        assertThatThrownBy(() -> service.initiate(me, req(21L, 21L, 1, 0, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("initiate: карта не найдена (entityManager.find -> null) -> CardNotFoundException")
    void initiate_cardNotFound() {
        Long me = 26L;
        when(userRepository.findById(me)).thenReturn(Optional.of(user(me)));
        when(entityManager.find(Card.class, 30L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);

        assertThatThrownBy(() -> service.initiate(me, req(30L, 31L, 1, 0, null)))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    @DisplayName("initiate: валидации request (amount<=0, ttl<0, ttl>max, null-поля)")
    void initiate_requestValidation() {
        assertThatThrownBy(() -> service.initiate(1L, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.initiate(1L, req(null, 2L, 1, 0, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.initiate(1L, req(1L, null, 1, 0, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.initiate(1L, req(1L, 2L, 0, 0, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.initiate(1L, req(1L, 2L, 1, -1, null)))
                .isInstanceOf(BadRequestException.class);

        when(props.getMaxTtlSeconds()).thenReturn(60);
        assertThatThrownBy(() -> service.initiate(1L, req(1L, 2L, 1, 61, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("initiate: инициатор не найден -> UserNotFoundException")
    void initiate_initiatorNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.initiate(999L, req(1L, 2L, 1, 0, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("cancel: ok -> перевод становится CANCELED, сохраняется, маппится")
    void cancel_ok() {
        Long me = 50L;
        Transfer t = new Transfer();
        t.setId(300L);
        t.setInitiator(user(me));
        t.setStatus(TransferStatus.PENDING);
        t.setExpiresAt(NOW.plus(10, ChronoUnit.MINUTES));

        when(transferRepository.findById(300L)).thenReturn(Optional.of(t));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferDto dto = new TransferDto();
        try (MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {
            tm.when(() -> TransferMapper.toDto(any(Transfer.class))).thenReturn(dto);

            TransferDto out = service.cancel(me, 300L);
            assertThat(out).isSameAs(dto);

            assertThat(t.getStatus()).isEqualTo(TransferStatus.CANCELED);
            assertThat(t.getFailureCode()).isEqualTo("CANCELED");
            assertThat(t.getFailureMessage()).isEqualTo("Canceled by user");

            verify(transferRepository).save(t);
        }
    }

    @Test
    @DisplayName("cancel: не инициатор -> OwnershipViolationException")
    void cancel_notInitiator() {
        Transfer t = new Transfer();
        t.setId(301L);
        t.setInitiator(user(1L));
        t.setStatus(TransferStatus.PENDING);
        t.setExpiresAt(NOW.plusMinutes(5));

        when(transferRepository.findById(301L)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.cancel(2L, 301L))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    @DisplayName("cancel: статус != PENDING -> TransferInvalidStateException")
    void cancel_notPending() {
        Transfer t = new Transfer();
        t.setId(302L);
        t.setInitiator(user(7L));
        t.setStatus(TransferStatus.COMPLETED);

        when(transferRepository.findById(302L)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.cancel(7L, 302L))
                .isInstanceOf(TransferInvalidStateException.class);
    }

    @Test
    @DisplayName("cancel: просрочен -> TransferExpiredException")
    void cancel_expired() {
        Transfer t = new Transfer();
        t.setId(303L);
        t.setInitiator(user(7L));
        t.setStatus(TransferStatus.PENDING);
        t.setExpiresAt(NOW.minusSeconds(1));

        when(transferRepository.findById(303L)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.cancel(7L, 303L))
                .isInstanceOf(TransferExpiredException.class);
    }

    @Test
    @DisplayName("cancel: не найден -> TransferNotFoundException")
    void cancel_notFound() {
        when(transferRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(1L, 404L))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    @DisplayName("listMy: строит Pageable (кламп размера), маппит Page -> PageDto")
    void listMy_ok() {
        Page<Transfer> page = new PageImpl<>(
                List.of(new Transfer()),
                PageRequest.of(1, 50, Sort.by(Sort.Direction.DESC, "id")),
                123
        );
        when(transferRepository.findByInitiator_Id(eq(9L), any(Pageable.class))).thenReturn(page);

        PageDto<TransferDto> dto = new PageDto<>();
        try (MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class);
             MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {

            tm.when(() -> TransferMapper.toDto(any(Transfer.class))).thenReturn(new TransferDto());
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<TransferDto> out = service.listMy(9L, 1, 999);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(transferRepository).findByInitiator_Id(eq(9L), cap.capture());
            Pageable p = cap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(1);
            assertThat(p.getPageSize()).isEqualTo(50);
            assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }

    @Test
    @DisplayName("listAll: Page -> PageDto, дефолт размера при size<=0")
    void listAll_ok() {
        Page<Transfer> page = new PageImpl<>(
                List.of(new Transfer(), new Transfer()),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")),
                2
        );
        when(transferRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageDto<TransferDto> dto = new PageDto<>();
        try (MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class);
             MockedStatic<TransferMapper> tm = Mockito.mockStatic(TransferMapper.class)) {

            tm.when(() -> TransferMapper.toDto(any(Transfer.class))).thenReturn(new TransferDto());
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<TransferDto> out = service.listAll(-1, 0);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(transferRepository).findAll(cap.capture());
            Pageable p = cap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(0);
            assertThat(p.getPageSize()).isEqualTo(20);
            assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }
}
