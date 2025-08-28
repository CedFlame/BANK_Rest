package com.example.bankcards.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "crypto")
public class CryptoProperties {

    @NotBlank
    private String aesKey;

    @NotBlank
    private String hmacKey;

    public byte[] aesKeyBytes() {
        byte[] k = decodeStrict(aesKey, "crypto.aes-key");
        int len = k.length;
        if (!(len == 16 || len == 24 || len == 32)) {
            throw new IllegalArgumentException("crypto.aes-key must decode to 16/24/32 bytes (was " + len + ")");
        }
        return k;
    }

    public byte[] hmacKeyBytes() {
        byte[] k = decodeStrict(hmacKey, "crypto.hmac-key");
        if (k.length < 32) {
            throw new IllegalArgumentException("crypto.hmac-key must decode to >= 32 bytes (was " + k.length + ")");
        }
        return k;
    }

    private static byte[] decodeStrict(String b64, String field) {
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a valid Base64 string", e);
        }
    }
}
