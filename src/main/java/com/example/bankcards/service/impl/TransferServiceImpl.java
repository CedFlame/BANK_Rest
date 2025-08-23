package com.example.bankcards.service.impl;

import com.example.bankcards.config.properties.TransfersProperties;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.entity.enums.TransferStatus;
import com.example.bankcards.exception.BadRequestException;
import com.example.bankcards.exception.CardExpiredException;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.IdempotencyConflictException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.OwnershipViolationException;
import com.example.bankcards.exception.TransferExpiredException;
import com.example.bankcards.exception.TransferInvalidStateException;
import com.example.bankcards.exception.TransferNotFoundException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.mapper.TransferMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.TransferService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final TransfersProperties props;
    private final TransferRepository transferRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    private record TwoCards(Card from, Card to) {}

    @Override
    @Transactional
    public TransferDto initiate(Long currentUserId, TransferRequest request) {
        validateRequest(request);
        User initiator = loadInitiator(currentUserId);

        Optional<Transfer> idem = findIdempotent(request);
        if (idem.isPresent()) {
            validateIdempotentSame(idem.get(), currentUserId, request);
            return TransferMapper.toDto(idem.get());
        }

        TwoCards cards = loadCardsLockedOrdered(request.getFromCardId(), request.getToCardId());
        validateOwnership(cards, currentUserId);
        ensureActiveAndNotExpired(cards.from());
        ensureActiveAndNotExpired(cards.to());
        ensureSufficientFunds(cards.from(), request.getAmount());

        LocalDateTime now = now();
        LocalDateTime expiresAt = calcExpiresAt(request, now);

        Transfer transfer = Transfer.builder()
                .initiator(initiator)
                .fromCard(cards.from())
                .toCard(cards.to())
                .amount(request.getAmount())
                .status(TransferStatus.PENDING)
                .expiresAt(expiresAt)
                .idempotencyKey(blankToNull(request.getIdempotencyKey()))
                .build();

        applyExecutionIfDue(transfer, cards.from(), cards.to(), now);

        try {
            transfer = transferRepository.save(transfer);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotencyRace(currentUserId, request, e);
        }

        cardRepository.save(cards.from());
        cardRepository.save(cards.to());
        return TransferMapper.toDto(transfer);
    }

    @Override
    @Transactional
    public TransferDto cancel(Long currentUserId, Long transferId) {
        Transfer t = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (!t.getInitiator().getId().equals(currentUserId)) {
            throw new OwnershipViolationException("Only initiator can cancel transfer");
        }
        if (t.getStatus() != TransferStatus.PENDING) {
            throw new TransferInvalidStateException("Only PENDING transfer can be canceled");
        }
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(now())) {
            throw new TransferExpiredException(transferId);
        }

        t.setStatus(TransferStatus.CANCELED);
        t.setFailureCode("CANCELED");
        t.setFailureMessage("Canceled by user");
        t = transferRepository.save(t);

        log.info("Transfer canceled: {}", transferId);
        return TransferMapper.toDto(t);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<TransferDto> listMy(Long userId, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<Transfer> p = transferRepository.findByInitiator_Id(userId, pageable);
        List<TransferDto> dtos = p.stream().map(TransferMapper::toDto).toList();
        return PageDtoMapper.toPageDto(p, dtos);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<TransferDto> listAll(int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<Transfer> p = transferRepository.findAll(pageable);
        List<TransferDto> dtos = p.stream().map(TransferMapper::toDto).toList();
        return PageDtoMapper.toPageDto(p, dtos);
    }

    private void validateRequest(TransferRequest r) {
        if (r == null) throw new BadRequestException("request is null");
        if (r.getFromCardId() == null) throw new BadRequestException("fromCardId is null");
        if (r.getToCardId() == null) throw new BadRequestException("toCardId is null");
        if (r.getAmount() == null || r.getAmount() <= 0) throw new BadRequestException("amount must be > 0");
        if (r.getTtlSeconds() != null && r.getTtlSeconds() < 0) throw new BadRequestException("ttlSeconds must be >= 0");
        if (r.getTtlSeconds() != null && props.getMaxTtlSeconds() > 0 && r.getTtlSeconds() > props.getMaxTtlSeconds()) {
            throw new BadRequestException("ttlSeconds exceeds limit");
        }
    }

    private User loadInitiator(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Optional<Transfer> findIdempotent(TransferRequest r) {
        return hasIdemKey(r) ? transferRepository.findByIdempotencyKey(r.getIdempotencyKey()) : Optional.empty();
    }

    private void validateIdempotentSame(Transfer t, Long currentUserId, TransferRequest r) {
        boolean same = t.getInitiator().getId().equals(currentUserId)
                && t.getFromCard().getId().equals(r.getFromCardId())
                && t.getToCard().getId().equals(r.getToCardId())
                && t.getAmount().equals(r.getAmount());
        if (!same) throw new IdempotencyConflictException();
    }

    private TwoCards loadCardsLockedOrdered(Long fromId, Long toId) {
        Card first  = lockCardForUpdate(Math.min(fromId, toId));
        Card second = lockCardForUpdate(Math.max(fromId, toId));
        Card from   = first.getId().equals(fromId) ? first : second;
        Card to     = from == first ? second : first;
        if (from.getId().equals(to.getId())) throw new BadRequestException("fromCardId equals toCardId");
        return new TwoCards(from, to);
    }

    private void validateOwnership(TwoCards cards, Long currentUserId) {
        Long ownerFromId = cards.from().getUser().getId();
        Long ownerToId   = cards.to().getUser().getId();
        if (!ownerFromId.equals(ownerToId)) {
            throw new OwnershipViolationException("Cards must belong to the same user");
        }
        if (!ownerFromId.equals(currentUserId)) {
            throw new OwnershipViolationException("Operation allowed only for own cards");
        }
    }

    private void ensureActiveAndNotExpired(Card c) {
        if (c.getStatus() != CardStatus.ACTIVE) {
            throw new com.example.bankcards.exception.InvalidCardStateException("Card is not ACTIVE: " + c.getId());
        }
        YearMonth nowYm = YearMonth.now(clock);
        if (c.getExpiry() != null && c.getExpiry().isBefore(nowYm)) {
            throw new CardExpiredException(c.getId());
        }
    }

    private void ensureSufficientFunds(Card from, long amount) {
        if (from.getBalance() < amount) throw new InsufficientFundsException(from.getId());
    }

    private void applyExecutionIfDue(Transfer t, Card from, Card to, LocalDateTime now) {
        boolean executeNow = (t.getExpiresAt() == null) || !t.getExpiresAt().isAfter(now);
        if (!executeNow) return;
        from.setBalance(Math.subtractExact(from.getBalance(), t.getAmount()));
        to.setBalance(Math.addExact(to.getBalance(), t.getAmount()));
        t.setStatus(TransferStatus.COMPLETED);
        t.setExecutedAt(now);
        log.info("Transfer completed: {} -> {} amount={} last4:{}->{}", from.getId(), to.getId(), t.getAmount(), from.getPanLast4(), to.getPanLast4());
    }

    private TransferDto handleIdempotencyRace(Long currentUserId, TransferRequest r, DataIntegrityViolationException e) {
        if (!hasIdemKey(r)) throw e;
        Transfer t = transferRepository.findByIdempotencyKey(r.getIdempotencyKey()).orElseThrow(() -> e);
        validateIdempotentSame(t, currentUserId, r);
        return TransferMapper.toDto(t);
    }

    private Card lockCardForUpdate(Long cardId) {
        Card c = entityManager.find(Card.class, cardId, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) throw new CardNotFoundException(cardId);
        return c;
    }

    private LocalDateTime calcExpiresAt(TransferRequest r, LocalDateTime now) {
        Integer ttl = r.getTtlSeconds();
        if (ttl == null || ttl <= 0) return null;
        return now.plusSeconds(ttl.longValue());
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clamp(size), DEFAULT_SORT);
    }

    private int clamp(int size) {
        if (size <= 0) return props.getDefaultPageSize();
        return Math.min(size, props.getMaxPageSize());
    }

    private boolean hasIdemKey(TransferRequest r) {
        return r.getIdempotencyKey() != null && !r.getIdempotencyKey().isBlank();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
