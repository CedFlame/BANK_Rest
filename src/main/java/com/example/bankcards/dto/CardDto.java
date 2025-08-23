package com.example.bankcards.dto;

import com.example.bankcards.entity.enums.CardStatus;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardDto {
    private Long id;
    private String maskedNumber;
    private String expiry;
    private CardStatus status;
    private Long balance;
}
