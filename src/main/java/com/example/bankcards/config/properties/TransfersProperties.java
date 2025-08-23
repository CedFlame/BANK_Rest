package com.example.bankcards.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "transfers")
public class TransfersProperties {

    private final int defaultPageSize;
    private final int maxPageSize;
    private final int maxTtlSeconds;

    public TransfersProperties(
            @Min(1) int defaultPageSize,
            @Min(1) int maxPageSize,
            @Min(0) int maxTtlSeconds
    ) {
        this.defaultPageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        this.maxPageSize = maxPageSize > 0 ? maxPageSize : 100;
        this.maxTtlSeconds = Math.max(maxTtlSeconds, 0);
    }
}
