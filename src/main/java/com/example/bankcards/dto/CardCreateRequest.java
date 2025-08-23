package com.example.bankcards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardCreateRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{16}$")
    private String pan;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}$")
    private String expiry;
}
