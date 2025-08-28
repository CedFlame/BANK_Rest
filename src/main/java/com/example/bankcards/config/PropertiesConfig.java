package com.example.bankcards.config;

import com.example.bankcards.config.properties.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        UsersProperties.class,
        CryptoProperties.class,
        TransfersProperties.class,
        TransfersSchedulerProperties.class,
        AuthRateLimitProperties.class,
})
public class PropertiesConfig {}