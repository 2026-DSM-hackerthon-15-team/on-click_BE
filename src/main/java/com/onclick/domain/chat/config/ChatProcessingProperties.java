package com.onclick.domain.chat.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.processing")
@Getter
@Setter
public class ChatProcessingProperties {

    private int maxAttempts = 3;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration retryDelay = Duration.ofSeconds(10);
    private int recoveryBatchSize = 50;

    public int safeMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public Duration safeLeaseDuration() {
        return positiveOrDefault(leaseDuration, Duration.ofMinutes(2));
    }

    public Duration safeRetryDelay() {
        return positiveOrDefault(retryDelay, Duration.ofSeconds(10));
    }

    public int safeRecoveryBatchSize() {
        return Math.max(1, recoveryBatchSize);
    }

    private Duration positiveOrDefault(Duration value, Duration defaultValue) {
        if (value == null || value.isZero() || value.isNegative()) {
            return defaultValue;
        }
        return value;
    }
}
