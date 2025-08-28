package com.example.bankcards.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "transfers.scheduler")
public class TransfersSchedulerProperties {

    public enum Mode { EXPIRE, EXECUTE }

    private boolean enabled = false;

    private Duration fixedDelay = Duration.ofSeconds(10);

    private int batchSize = 100;

    private Mode mode = Mode.EXPIRE;
}
