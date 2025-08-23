package com.example.bankcards.mapper;

import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.entity.Transfer;

public class TransferMapper {
    public static TransferDto toDto(Transfer t) {
        if (t == null) return null;
        return TransferDto.builder()
                .id(t.getId())
                .fromCardId(t.getFromCard().getId())
                .toCardId(t.getToCard().getId())
                .fromLast4(t.getFromCard().getPanLast4())
                .toLast4(t.getToCard().getPanLast4())
                .amount(t.getAmount())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .expiresAt(t.getExpiresAt())
                .executedAt(t.getExecutedAt())
                .failureCode(t.getFailureCode())
                .failureMessage(t.getFailureMessage())
                .build();
    }
}
