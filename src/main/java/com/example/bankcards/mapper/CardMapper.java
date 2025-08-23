package com.example.bankcards.mapper;

import com.example.bankcards.dto.CardDto;
import com.example.bankcards.entity.Card;

import java.time.format.DateTimeFormatter;

public final class CardMapper {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private CardMapper() {}

    public static CardDto toDto(Card card) {
        if (card == null) return null;
        return CardDto.builder()
                .id(card.getId())
                .maskedNumber(mask(card.getPanLast4()))
                .expiry(card.getExpiry() == null ? null : card.getExpiry().format(YM))
                .status(card.getStatus())
                .balance(card.getBalance())
                .build();
    }

    private static String mask(String last4) {
        String l4 = (last4 == null || last4.length() != 4) ? "****" : last4;
        return "**** **** **** " + l4;
    }
}
