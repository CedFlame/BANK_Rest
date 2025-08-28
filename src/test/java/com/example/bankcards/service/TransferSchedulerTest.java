package com.example.bankcards.service;

import com.example.bankcards.config.properties.TransfersSchedulerProperties;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.TransferStatus;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.Method;
import java.time.*;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSchedulerTest {

    @Mock TransfersSchedulerProperties props;
    @Mock TransferRepository transferRepository;
    @Mock CardRepository cardRepository;
    @Mock EntityManager entityManager;
    @Mock Clock clock;
    @Mock TaskScheduler taskScheduler;

    @InjectMocks TransferScheduler scheduler;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-08-25T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime NOW = LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone());
    private final YearMonth NOW_YM = YearMonth.from(NOW);

    @BeforeEach
    void time() {
        when(clock.instant()).thenReturn(fixedClock.instant());
        when(clock.getZone()).thenReturn(fixedClock.getZone());
    }

    private static Card card(long id, CardStatus status, YearMonth expiry, long balance) {
        Card c = new Card();
        c.setId(id);
        c.setStatus(status);
        c.setExpiry(expiry);
        c.setBalance(balance);
        return c;
    }

    private static Transfer transfer(long id, Card from, Card to, long amount,
                                     TransferStatus status, LocalDateTime expiresAt) {
        Transfer t = new Transfer();
        t.setId(id);
        t.setFromCard(from);
        t.setToCard(to);
        t.setAmount(amount);
        t.setStatus(status);
        t.setExpiresAt(expiresAt);
        return t;
    }

    private int callProcessBatch(TransferScheduler target) {
        try {
            Method m = TransferScheduler.class.getDeclaredMethod("processBatch");
            m.setAccessible(true);
            return (int) m.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void callSafeTick(TransferScheduler target) {
        try {
            Method m = TransferScheduler.class.getDeclaredMethod("safeTick");
            m.setAccessible(true);
            m.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("processSingle: EXECUTE happy path -> перевод выполнен, балансы обновлены")
    void processSingle_execute_ok() {
        when(props.getMode()).thenReturn(TransfersSchedulerProperties.Mode.EXECUTE);

        Card from = card(1L, CardStatus.ACTIVE, NOW_YM.plusMonths(1), 1_000);
        Card to   = card(2L, CardStatus.ACTIVE, NOW_YM.plusMonths(1),   100);
        Transfer t = transfer(10L, from, to, 300, TransferStatus.PENDING, NOW.minusSeconds(1));

        when(entityManager.find(Transfer.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t);
        when(entityManager.find(Card.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        scheduler.processSingle(10L);

        assertThat(from.getBalance()).isEqualTo(700);
        assertThat(to.getBalance()).isEqualTo(400);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(t.getExecutedAt()).isEqualTo(NOW);

        verify(cardRepository).save(from);
        verify(cardRepository).save(to);
        verify(transferRepository).save(t);
    }

    @Test
    @DisplayName("processSingle: EXECUTE -> недостаточно средств")
    void processSingle_execute_insufficientFunds() {
        when(props.getMode()).thenReturn(TransfersSchedulerProperties.Mode.EXECUTE);

        Card from = card(1L, CardStatus.ACTIVE, NOW_YM.plusMonths(1), 200);
        Card to   = card(2L, CardStatus.ACTIVE, NOW_YM.plusMonths(1), 100);
        Transfer t = transfer(11L, from, to, 300, TransferStatus.PENDING, NOW.minusMinutes(1));

        when(entityManager.find(Transfer.class, 11L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t);
        when(entityManager.find(Card.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        scheduler.processSingle(11L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(t.getFailureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(cardRepository, never()).save(any());
        verify(transferRepository).save(t);
    }

    @Test
    @DisplayName("processSingle: EXECUTE -> одна из карт заблокирована/просрочена")
    void processSingle_execute_badCardState() {
        when(props.getMode()).thenReturn(TransfersSchedulerProperties.Mode.EXECUTE);

        Card from = card(5L, CardStatus.BLOCKED, NOW_YM.plusMonths(1), 1_000); // плохое состояние
        Card to   = card(7L, CardStatus.ACTIVE, NOW_YM.plusMonths(1),   100);
        Transfer t = transfer(12L, from, to, 300, TransferStatus.PENDING, NOW.minusSeconds(1));

        when(entityManager.find(Transfer.class, 12L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t);
        when(entityManager.find(Card.class, 5L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(from);
        when(entityManager.find(Card.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(to);

        scheduler.processSingle(12L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(t.getFailureCode()).isEqualTo("CARD_STATE");
        verify(cardRepository, never()).save(any());
        verify(transferRepository).save(t);
    }

    @Test
    @DisplayName("processSingle: EXPIRE режим -> перевод помечен EXPIRED")
    void processSingle_expireMode() {
        when(props.getMode()).thenReturn(TransfersSchedulerProperties.Mode.EXPIRE);

        Card from = card(1L, CardStatus.ACTIVE, NOW_YM.plusMonths(1), 1_000);
        Card to   = card(2L, CardStatus.ACTIVE, NOW_YM.plusMonths(1),   100);
        Transfer t = transfer(13L, from, to, 300, TransferStatus.PENDING, NOW.minusSeconds(1));

        when(entityManager.find(Transfer.class, 13L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t);

        scheduler.processSingle(13L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.EXPIRED);
        assertThat(t.getFailureCode()).isEqualTo("EXPIRED");
        verify(transferRepository).save(t);
        verifyNoInteractions(cardRepository);
    }

    @Test
    @DisplayName("processSingle: transfer не найден -> no-op")
    void processSingle_notFound_noop() {
        when(entityManager.find(Transfer.class, 999L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        scheduler.processSingle(999L);
        verifyNoInteractions(cardRepository, transferRepository);
    }

    @Test
    @DisplayName("processSingle: статус != PENDING -> no-op")
    void processSingle_notPending_noop() {
        Transfer t = new Transfer();
        t.setId(20L);
        t.setStatus(TransferStatus.COMPLETED);
        when(entityManager.find(Transfer.class, 20L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t);

        scheduler.processSingle(20L);
        verifyNoInteractions(cardRepository, transferRepository);
    }

    @Test
    @DisplayName("processSingle: expiresAt == null или в будущем -> no-op")
    void processSingle_notExpired_noop() {
        Transfer t1 = new Transfer();
        t1.setId(21L);
        t1.setStatus(TransferStatus.PENDING);
        t1.setExpiresAt(null);

        Transfer t2 = new Transfer();
        t2.setId(22L);
        t2.setStatus(TransferStatus.PENDING);
        t2.setExpiresAt(NOW.plusSeconds(1));

        when(entityManager.find(Transfer.class, 21L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t1);
        when(entityManager.find(Transfer.class, 22L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(t2);

        scheduler.processSingle(21L);
        scheduler.processSingle(22L);

        verifyNoInteractions(cardRepository, transferRepository);
    }

    @Test
    @DisplayName("processBatch: корректный PageRequest (size=max(1,batchSize), sort ASC by id), вызывает processSingle по id")
    void processBatch_ok() {
        when(props.getBatchSize()).thenReturn(5);

        Transfer t = new Transfer(); t.setId(100L);
        var page = new PageImpl<>(
                List.of(t),
                PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id")),
                1
        );

        when(transferRepository.findByStatusAndExpiresAtBefore(
                eq(TransferStatus.PENDING), eq(NOW), any(PageRequest.class)))
                .thenReturn(page);

        TransferScheduler spy = Mockito.spy(scheduler);
        doNothing().when(spy).processSingle(100L);

        int processed = callProcessBatch(spy);

        assertThat(processed).isEqualTo(1);

        ArgumentCaptor<PageRequest> pr = ArgumentCaptor.forClass(PageRequest.class);
        verify(transferRepository).findByStatusAndExpiresAtBefore(eq(TransferStatus.PENDING), eq(NOW), pr.capture());
        PageRequest used = pr.getValue();
        assertThat(used.getPageNumber()).isEqualTo(0);
        assertThat(used.getPageSize()).isEqualTo(5); // max(1, batchSize)
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));

        verify(spy).processSingle(100L);
    }

    @Test
    @DisplayName("processBatch: исключение на одном id не срывает цикл (счётчик учитывает только успешные)")
    void processBatch_partialFailures() {
        when(props.getBatchSize()).thenReturn(3);

        Transfer t1 = new Transfer(); t1.setId(1L);
        Transfer t2 = new Transfer(); t2.setId(2L);
        Transfer t3 = new Transfer(); t3.setId(3L);

        var page = new PageImpl<>(List.of(t1, t2, t3));
        when(transferRepository.findByStatusAndExpiresAtBefore(eq(TransferStatus.PENDING), eq(NOW), any(PageRequest.class)))
                .thenReturn(page);

        TransferScheduler spy = Mockito.spy(scheduler);
        doNothing().when(spy).processSingle(1L);
        doThrow(new RuntimeException("boom")).when(spy).processSingle(2L);
        doNothing().when(spy).processSingle(3L);

        int processed = callProcessBatch(spy);
        assertThat(processed).isEqualTo(2);
    }

    @Test
    @DisplayName("safeTick: перехватывает исключение из processBatch")
    void safeTick_swallowsExceptions() {
        TransferScheduler spy = Mockito.spy(scheduler);

        doThrow(new RuntimeException("oops")).when(spy).processBatch();

        assertDoesNotThrow(() -> {
            var m = TransferScheduler.class.getDeclaredMethod("safeTick");
            m.setAccessible(true);
            m.invoke(spy);
        });
    }



    @Test
    @DisplayName("init: enabled -> запускает scheduleWithFixedDelay с фиксированной задержкой")
    void init_enabledSchedules() {
        when(props.isEnabled()).thenReturn(true);
        when(props.getFixedDelay()).thenReturn(Duration.ofSeconds(15));

        scheduler.init();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(15)));
    }

    @Test
    @DisplayName("init: disabled -> не планирует задачу")
    void init_disabledDoesNotSchedule() {
        when(props.isEnabled()).thenReturn(false);

        scheduler.init();

        verifyNoInteractions(taskScheduler);
    }

    @Test
    @DisplayName("shutdown: отменяет future.cancel(false) если был запущен")
    void shutdown_cancelsFuture() {
        when(props.isEnabled()).thenReturn(true);
        when(props.getFixedDelay()).thenReturn(Duration.ofSeconds(1));

        @SuppressWarnings("unchecked")
        ScheduledFuture<?> f = mock(ScheduledFuture.class);

        doReturn(f).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        scheduler.init();
        scheduler.shutdown();

        verify(f).cancel(false);
    }

}
