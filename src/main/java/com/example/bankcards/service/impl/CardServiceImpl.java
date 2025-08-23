package com.example.bankcards.service.impl;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.exception.CardAlreadyExistsException;
import com.example.bankcards.exception.CardExpiredException;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.InvalidCardStateException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.CardMapper;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.crypto.CryptoService;
import com.example.bankcards.security.crypto.HmacService;
import com.example.bankcards.service.CardService;
import com.example.bankcards.util.CardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final HmacService hmacService;
    private final Clock clock;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    @Override
    @Transactional
    public CardDto createForUser(Long userId, CardCreateRequest req) {
        User owner = findOwner(userId);
        String pan = normalizeAndValidatePan(req.getPan());
        String panHash = ensurePanUniqueAndGetHash(pan);
        YearMonth expiry = CardUtils.parseExpiry(req.getExpiry());
        validateNotPastExpiry(expiry);

        Card card = buildCard(owner, pan, panHash, expiry);
        card = cardRepository.save(card);

        log.info("Card created for user {} ****{}", owner.getId(), card.getPanLast4());
        return CardMapper.toDto(card);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<CardDto> listMy(Long userId, int page, int size, CardFilter filter) {
        Pageable pageable = pageable(page, size);
        Page<Card> pageData = (filter != null && filter.getStatus() != null)
                ? cardRepository.findByUser_IdAndStatus(userId, filter.getStatus(), pageable)
                : cardRepository.findByUser_Id(userId, pageable);
        return toPageDto(pageData);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<CardDto> listAll(int page, int size, CardFilter filter) {
        Pageable pageable = pageable(page, size);
        Page<Card> pageData = (filter != null && filter.getStatus() != null)
                ? cardRepository.findByStatus(filter.getStatus(), pageable)
                : cardRepository.findAll(pageable);
        return toPageDto(pageData);
    }

    @Override
    @Transactional
    public CardDto block(Long cardId) {
        CardDto dto = updateStatus(cardId, CardStatus.BLOCKED);
        log.info("Card blocked: {}", cardId);
        return dto;
    }

    @Override
    @Transactional
    public CardDto activate(Long cardId) {
        CardDto dto = updateStatus(cardId, CardStatus.ACTIVE);
        log.info("Card activated: {}", cardId);
        return dto;
    }

    @Override
    @Transactional
    public void delete(Long cardId) {
        Card c = getCardOrThrow(cardId);
        cardRepository.delete(c);
        log.info("Card deleted: {}", cardId);
    }

    private User findOwner(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private String normalizeAndValidatePan(String rawPan) {
        String pan = CardUtils.normalizePan(rawPan);
        CardUtils.validatePan16(pan);
        return pan;
    }

    private String ensurePanUniqueAndGetHash(String pan) {
        String panHash = hmacService.hmacHex(pan);
        if (cardRepository.existsByPanHash(panHash)) throw new CardAlreadyExistsException();
        return panHash;
    }

    private void validateNotPastExpiry(YearMonth expiry) {
        if (expiry.isBefore(YearMonth.now(clock))) {
            throw new InvalidCardStateException("Expiry date is in the past");
        }
    }

    private Card buildCard(User owner, String pan, String panHash, YearMonth expiry) {
        String last4 = CardUtils.last4(pan);
        return Card.builder()
                .panCiphertext(cryptoService.encryptPan(pan))
                .panHash(panHash)
                .panLast4(last4)
                .expiry(expiry)
                .status(CardStatus.ACTIVE)
                .balance(0L)
                .user(owner)
                .build();
    }

    private Card getCardOrThrow(Long cardId) {
        return cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
    }

    private CardDto updateStatus(Long cardId, CardStatus to) {
        Card c = getCardOrThrow(cardId);
        validateTransition(c, to);
        c.setStatus(to);
        c = cardRepository.save(c);
        return CardMapper.toDto(c);
    }

    private void validateTransition(Card c, CardStatus to) {
        if (c.getExpiry() != null && c.getExpiry().isBefore(YearMonth.now(clock))) {
            throw new CardExpiredException(c.getId());
        }
        if (c.getStatus() == to) {
            throw new InvalidCardStateException("Card already has status " + to);
        }
        if (to == CardStatus.ACTIVE && c.getStatus() != CardStatus.BLOCKED) {
            throw new InvalidCardStateException("Only blocked card can be activated");
        }
        if (to == CardStatus.BLOCKED && c.getStatus() != CardStatus.ACTIVE) {
            throw new InvalidCardStateException("Only active card can be blocked");
        }
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clamp(size), DEFAULT_SORT);
    }

    private static int clamp(int size) {
        if (size <= 0) return 10;
        return Math.min(size, 100);
    }

    private PageDto<CardDto> toPageDto(Page<Card> pageData) {
        List<CardDto> dtos = pageData.stream().map(CardMapper::toDto).toList();
        return PageDtoMapper.toPageDto(pageData, dtos);
    }
}
