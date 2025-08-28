package com.example.bankcards.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "security.ratelimit.auth")
public class AuthRateLimitProperties {
    private boolean enabled = true;

    @Min(1)
    private int capacity = 20;

    @Min(1)
    private int windowSeconds = 60;

    private Set<String> paths = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );
}
