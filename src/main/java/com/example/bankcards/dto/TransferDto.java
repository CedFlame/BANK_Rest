package com.example.bankcards.dto;

import com.example.bankcards.entity.enums.TransferStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TransferDto {
    private Long id;
    private Long fromCardId;
    private Long toCardId;
    private String fromLast4;
    private String toLast4;
    private Long amount;
    private TransferStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime executedAt;
    private String failureCode;
    private String failureMessage;
}
