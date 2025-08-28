package com.example.bankcards.service;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.exception.*;
import com.example.bankcards.mapper.CardMapper;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.crypto.CryptoService;
import com.example.bankcards.security.crypto.HmacService;
import com.example.bankcards.service.impl.CardServiceImpl;
import com.example.bankcards.util.CardUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;

import java.time.*;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardServiceImplTest {

    @Mock CardRepository cardRepository;
    @Mock UserRepository userRepository;
    @Mock TransferRepository transferRepository;
    @Mock CryptoService cryptoService;
    @Mock HmacService hmacService;
    @Mock Clock clock;

    @InjectMocks
    CardServiceImpl service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-08-25T03:00:00Z"), ZoneOffset.UTC);
    private final YearMonth NOW_YM = YearMonth.from(LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone()));

    @BeforeEach
    void commonTime() {
        when(clock.instant()).thenAnswer(inv -> fixedClock.instant());
        when(clock.getZone()).thenAnswer(inv -> fixedClock.getZone());
    }

    private static User owner(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("u"+id);
        return u;
    }

    private static Card card(long id, long ownerId, CardStatus status, YearMonth expiry, long balance, String last4) {
        Card c = new Card();
        c.setId(id);
        c.setUser(owner(ownerId));
        c.setStatus(status);
        c.setExpiry(expiry);
        c.setBalance(balance);
        c.setPanLast4(last4);
        return c;
    }

    @Test
    @DisplayName("createForUser: happy path -> сохраняет и маппит")
    void create_ok() {
        long userId = 10L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner(userId)));
        when(hmacService.hmacHex("4111111111111111")).thenReturn("H");
        when(cardRepository.existsByPanHash("H")).thenReturn(false);
        when(cryptoService.encryptPan("4111111111111111")).thenReturn("CIPHER");

        CardCreateRequest req = new CardCreateRequest();
        req.setPan(" 4111 1111 1111 1111 ");
        req.setExpiry("12/30");

        try (MockedStatic<CardUtils> cu = Mockito.mockStatic(CardUtils.class);
             MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class)) {

            cu.when(() -> CardUtils.normalizePan(anyString())).thenReturn("4111111111111111");
            cu.when(() -> CardUtils.validatePan16("4111111111111111")).thenAnswer(inv -> null);
            cu.when(() -> CardUtils.parseExpiry("12/30")).thenReturn(YearMonth.of(2030, 12));
            cu.when(() -> CardUtils.last4("4111111111111111")).thenReturn("1111");

            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(77L);
                return c;
            });

            CardDto dto = new CardDto();
            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(dto);

            CardDto out = service.createForUser(userId, req);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Card> cap = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(cap.capture());
            Card saved = cap.getValue();
            assertThat(saved.getUser().getId()).isEqualTo(userId);
            assertThat(saved.getPanHash()).isEqualTo("H");
            assertThat(saved.getPanCiphertext()).isEqualTo("CIPHER");
            assertThat(saved.getPanLast4()).isEqualTo("1111");
            assertThat(saved.getStatus()).isEqualTo(CardStatus.ACTIVE);
            assertThat(saved.getExpiry()).isEqualTo(YearMonth.of(2030, 12));
        }
    }

    @Test
    @DisplayName("createForUser: владелец не найден -> UserNotFoundException")
    void create_ownerNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        CardCreateRequest req = new CardCreateRequest();
        req.setPan("4111111111111111");
        req.setExpiry("12/30");

        assertThatThrownBy(() -> service.createForUser(1L, req))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("createForUser: PAN уже существует (по hash) -> CardAlreadyExistsException")
    void create_duplicatePan() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner(2L)));
        try (MockedStatic<CardUtils> cu = Mockito.mockStatic(CardUtils.class)) {
            cu.when(() -> CardUtils.normalizePan(anyString())).thenReturn("4111111111111111");
            cu.when(() -> CardUtils.validatePan16(anyString())).thenAnswer(inv -> null);
            cu.when(() -> CardUtils.parseExpiry(anyString())).thenReturn(NOW_YM.plusMonths(1));
            when(hmacService.hmacHex("4111111111111111")).thenReturn("H");
            when(cardRepository.existsByPanHash("H")).thenReturn(true);

            CardCreateRequest req = new CardCreateRequest();
            req.setPan("4111111111111111");
            req.setExpiry("08/30");

            assertThatThrownBy(() -> service.createForUser(2L, req))
                    .isInstanceOf(CardAlreadyExistsException.class);
        }
    }

    @Test
    @DisplayName("createForUser: expiry в прошлом -> InvalidCardStateException")
    void create_pastExpiry() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(owner(3L)));
        try (MockedStatic<CardUtils> cu = Mockito.mockStatic(CardUtils.class)) {
            cu.when(() -> CardUtils.normalizePan(anyString())).thenReturn("4111111111111111");
            cu.when(() -> CardUtils.validatePan16(anyString())).thenAnswer(inv -> null);
            cu.when(() -> CardUtils.parseExpiry(anyString())).thenReturn(NOW_YM.minusMonths(1));

            CardCreateRequest req = new CardCreateRequest();
            req.setPan("4111111111111111");
            req.setExpiry("01/20");

            assertThatThrownBy(() -> service.createForUser(3L, req))
                    .isInstanceOf(InvalidCardStateException.class);
        }
    }

    @Test
    @DisplayName("listMy: без фильтра -> findByUser_Id(pageable), маппит в PageDto")
    void listMy_noFilter() {
        Page<Card> page = new PageImpl<>(
                List.of(new Card(), new Card()),
                PageRequest.of(1, 50, Sort.by(Sort.Direction.DESC, "id")),
                123
        );
        when(cardRepository.findByUser_Id(eq(7L), any(Pageable.class))).thenReturn(page);

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class);
             MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {

            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(new CardDto());
            PageDto<CardDto> dto = new PageDto<>();
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<CardDto> out = service.listMy(7L, 1, 999, null);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findByUser_Id(eq(7L), cap.capture());
            Pageable p = cap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(1);
            assertThat(p.getPageSize()).isEqualTo(100); // clamp до 100 (запросили 999)
            assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }

    @Test
    @DisplayName("listMy: c фильтром по статусу -> findByUser_IdAndStatus")
    void listMy_withFilter() {
        CardFilter f = new CardFilter();
        f.setStatus(CardStatus.BLOCKED);

        Page<Card> page = new PageImpl<>(
                List.of(new Card()),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")),
                1
        );
        when(cardRepository.findByUser_IdAndStatus(eq(5L), eq(CardStatus.BLOCKED), any(Pageable.class)))
                .thenReturn(page);

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class);
             MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {

            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(new CardDto());
            PageDto<CardDto> dto = new PageDto<>();
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<CardDto> out = service.listMy(5L, -1, 0, f);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findByUser_IdAndStatus(eq(5L), eq(CardStatus.BLOCKED), cap.capture());
            Pageable p = cap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(0);
            assertThat(p.getPageSize()).isEqualTo(10); // default when size<=0
            assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }

    @Test
    @DisplayName("listAll: без фильтра -> findAll(pageable)")
    void listAll_noFilter() {
        Page<Card> page = new PageImpl<>(
                List.of(new Card(), new Card(), new Card()),
                PageRequest.of(2, 100, Sort.by(Sort.Direction.DESC, "id")),
                300
        );
        when(cardRepository.findAll(any(Pageable.class))).thenReturn(page);

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class);
             MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {

            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(new CardDto());
            PageDto<CardDto> dto = new PageDto<>();
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<CardDto> out = service.listAll(2, 1000, null);
            assertThat(out).isSameAs(dto);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findAll(cap.capture());
            Pageable p = cap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(2);
            assertThat(p.getPageSize()).isEqualTo(100); // clamp до 100
        }
    }

    @Test
    @DisplayName("listAll: c фильтром -> findByStatus")
    void listAll_withFilter() {
        CardFilter f = new CardFilter();
        f.setStatus(CardStatus.ACTIVE);

        Page<Card> page = new PageImpl<>(
                List.of(new Card()),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")),
                1
        );
        when(cardRepository.findByStatus(eq(CardStatus.ACTIVE), any(Pageable.class))).thenReturn(page);

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class);
             MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {

            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(new CardDto());
            PageDto<CardDto> dto = new PageDto<>();
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(dto);

            PageDto<CardDto> out = service.listAll(0, 10, f);
            assertThat(out).isSameAs(dto);

            verify(cardRepository).findByStatus(eq(CardStatus.ACTIVE), any(Pageable.class));
        }
    }

    @Test
    @DisplayName("block: ACTIVE -> BLOCKED")
    void block_ok() {
        Card existing = card(100L, 1L, CardStatus.ACTIVE, NOW_YM.plusMonths(1), 0, "1234");
        when(cardRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class)) {
            CardDto dto = new CardDto();
            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(dto);

            CardDto out = service.block(100L);
            assertThat(out).isSameAs(dto);
            assertThat(existing.getStatus()).isEqualTo(CardStatus.BLOCKED);
        }
    }

    @Test
    @DisplayName("activate: BLOCKED -> ACTIVE")
    void activate_ok() {
        Card existing = card(101L, 1L, CardStatus.BLOCKED, NOW_YM.plusYears(1), 0, "1234");
        when(cardRepository.findById(101L)).thenReturn(Optional.of(existing));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<CardMapper> cm = Mockito.mockStatic(CardMapper.class)) {
            CardDto dto = new CardDto();
            cm.when(() -> CardMapper.toDto(any(Card.class))).thenReturn(dto);

            CardDto out = service.activate(101L);
            assertThat(out).isSameAs(dto);
            assertThat(existing.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }
    }

    @Test
    @DisplayName("block: уже BLOCKED -> InvalidCardStateException")
    void block_alreadyBlocked() {
        Card existing = card(200L, 1L, CardStatus.BLOCKED, NOW_YM.plusMonths(2), 0, "0000");
        when(cardRepository.findById(200L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.block(200L))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    @DisplayName("activate: не BLOCKED -> InvalidCardStateException")
    void activate_wrongFromState() {
        Card existing = card(201L, 1L, CardStatus.ACTIVE, NOW_YM.plusMonths(2), 0, "0000");
        when(cardRepository.findById(201L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.activate(201L))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    @DisplayName("block/activate: срок карты истёк -> CardExpiredException")
    void changeStatus_expired() {
        Card expired = card(202L, 1L, CardStatus.ACTIVE, NOW_YM.minusMonths(1), 0, "0000");
        when(cardRepository.findById(202L)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.block(202L))
                .isInstanceOf(CardExpiredException.class);
    }

    @Test
    @DisplayName("block/activate: карта не найдена -> CardNotFoundException")
    void changeStatus_notFound() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.block(999L))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    @DisplayName("delete: нет переводов -> удаляет")
    void delete_ok() {
        Card c = card(300L, 1L, CardStatus.ACTIVE, NOW_YM.plusMonths(3), 0, "1234");
        when(cardRepository.findById(300L)).thenReturn(Optional.of(c));
        when(transferRepository.existsByFromCard_Id(300L)).thenReturn(false);
        when(transferRepository.existsByToCard_Id(300L)).thenReturn(false);

        service.delete(300L);

        verify(cardRepository).delete(c);
    }

    @Test
    @DisplayName("delete: есть переводы -> CardDeletionNotAllowedException")
    void delete_withTransfers() {
        Card c = card(301L, 1L, CardStatus.ACTIVE, NOW_YM.plusMonths(3), 0, "1234");
        when(cardRepository.findById(301L)).thenReturn(Optional.of(c));
        when(transferRepository.existsByFromCard_Id(301L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(301L))
                .isInstanceOf(CardDeletionNotAllowedException.class);
    }

    @Test
    @DisplayName("delete: карта не найдена -> CardNotFoundException")
    void delete_notFound() {
        when(cardRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(CardNotFoundException.class);
    }
}
