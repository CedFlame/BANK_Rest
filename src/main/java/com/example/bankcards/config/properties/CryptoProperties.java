package com.example.bankcards.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "crypto")
public class CryptoProperties {
    @NotBlank
    @Size(min = 32)
    private String aesKey;

    @NotBlank
    @Size(min = 32)
    private String hmacKey;
}
