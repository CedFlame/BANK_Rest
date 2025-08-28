// src/main/java/com/example/bankcards/config/properties/AuthRateLimitProperties.java
package com.example.bankcards.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.ratelimit.auth")
public class AuthRateLimitProperties {
    private boolean enabled = true;
    private int capacity = 20;                 // запросов за окно
    private int windowSeconds = 60;            // длина окна
    private List<String> paths = List.of(      // какие пути ограничивать
            "/api/auth/login",
            "/api/auth/register"
    );
}
