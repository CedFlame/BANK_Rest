package com.example.bankcards.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(1000)
    private long expirationMs;

    public SecretKey secretKey() {
        byte[] k = decodeStrict(secret, "jwt.secret");
        if (k.length < 32) {
            throw new IllegalArgumentException("jwt.secret must decode to >= 32 bytes (was " + k.length + ")");
        }
        return new SecretKeySpec(k, "HmacSHA256");
    }

    private static byte[] decodeStrict(String b64, String field) {
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a valid Base64 string", e);
        }
    }
}
