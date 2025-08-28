package com.example.bankcards.service;

import com.example.bankcards.config.properties.TransfersSchedulerProperties;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.TransferStatus;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferScheduler {

    private final TransfersSchedulerProperties props;
    private final TransferRepository transferRepository;
    private final CardRepository cardRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;

    private static final Sort PICK_SORT = Sort.by(Sort.Direction.ASC, "id");

    @PostConstruct
    void init() {
        if (!props.isEnabled()) {
            log.info("TransferScheduler disabled by config");
            return;
        }
        Duration delay = props.getFixedDelay();
        future = taskScheduler.scheduleWithFixedDelay(this::safeTick, delay);
        log.info("TransferScheduler started with fixedDelay={}", delay);
    }

    @PreDestroy
    void shutdown() {
        if (future != null) {
            future.cancel(false);
            log.info("TransferScheduler stopped");
        }
    }

    private void safeTick() {
        try {
            int processed = processBatch();
            if (processed > 0) {
                log.info("Scheduler processed {} transfers (mode={})", processed, props.getMode());
            }
        } catch (Exception e) {
            log.warn("Scheduler tick failed: {}", e.getMessage(), e);
        }
    }

    int processBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        var page = transferRepository.findByStatusAndExpiresAtBefore(
                TransferStatus.PENDING,
                now,
                PageRequest.of(0, Math.max(1, props.getBatchSize()), PICK_SORT)
        );
        List<Long> ids = page.getContent().stream().map(Transfer::getId).toList();
        int cnt = 0;
        for (Long id : ids) {
            try {
                processSingle(id);
                cnt++;
            } catch (Exception e) {
                log.warn("Scheduler failed to process transfer {}: {}", id, e.getMessage());
            }
        }
        return cnt;
    }

    @Transactional
    protected void processSingle(Long transferId) {
        LocalDateTime now = LocalDateTime.now(clock);

        Transfer t = entityManager.find(Transfer.class, transferId, LockModeType.PESSIMISTIC_WRITE);
        if (t == null) return;
        if (t.getStatus() != TransferStatus.PENDING) return;
        if (t.getExpiresAt() == null || t.getExpiresAt().isAfter(now)) return;

        if (props.getMode() == TransfersSchedulerProperties.Mode.EXECUTE) {
            executeExpiredTransfer(t, now);
        } else {
            expireTransfer(t);
        }
    }

    private void expireTransfer(Transfer t) {
        t.setStatus(TransferStatus.EXPIRED);
        t.setFailureCode("EXPIRED");
        t.setFailureMessage("Transfer expired");
        transferRepository.save(t);
        log.info("Transfer {} marked EXPIRED", t.getId());
    }

    private void executeExpiredTransfer(Transfer t, LocalDateTime now) {
        Long fromId = t.getFromCard().getId();
        Long toId = t.getToCard().getId();
        Card first  = entityManager.find(Card.class, Math.min(fromId, toId), LockModeType.PESSIMISTIC_WRITE);
        Card second = entityManager.find(Card.class, Math.max(fromId, toId), LockModeType.PESSIMISTIC_WRITE);
        Card from   = first.getId().equals(fromId) ? first : second;
        Card to     = from == first ? second : first;

        if (!isActiveAndNotExpired(from) || !isActiveAndNotExpired(to)) {
            t.setStatus(TransferStatus.FAILED);
            t.setFailureCode("CARD_STATE");
            t.setFailureMessage("Card is blocked or expired");
            transferRepository.save(t);
            log.info("Transfer {} FAILED due to card state", t.getId());
            return;
        }

        long amount = t.getAmount();
        if (from.getBalance() < amount) {
            t.setStatus(TransferStatus.FAILED);
            t.setFailureCode("INSUFFICIENT_FUNDS");
            t.setFailureMessage("Insufficient funds");
            transferRepository.save(t);
            log.info("Transfer {} FAILED due to insufficient funds", t.getId());
            return;
        }

        from.setBalance(Math.subtractExact(from.getBalance(), amount));
        to.setBalance(Math.addExact(to.getBalance(), amount));
        t.setStatus(TransferStatus.COMPLETED);
        t.setExecutedAt(now);

        cardRepository.save(from);
        cardRepository.save(to);
        transferRepository.save(t);

        log.info("Transfer {} auto-executed ({} -> {}) amount={}", t.getId(), from.getId(), to.getId(), amount);
    }

    private boolean isActiveAndNotExpired(Card c) {
        if (c.getStatus() != CardStatus.ACTIVE) return false;
        YearMonth nowYm = YearMonth.now(clock);
        return c.getExpiry() == null || !c.getExpiry().isBefore(nowYm);
    }
}
