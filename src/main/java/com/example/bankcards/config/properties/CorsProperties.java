package com.example.bankcards.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.cors")
public class CorsProps {
    private boolean enabled = false;                          // главный флаг
    private List<String> allowedOrigins = List.of("http://localhost:3000");
    private List<String> allowedMethods = List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS");
    private List<String> allowedHeaders = List.of("Authorization","Content-Type","X-Requested-With");
    private List<String> exposedHeaders = List.of("Authorization");
    private boolean allowCredentials = false;
    private long maxAge = 3600; // seconds
}
