package com.onclick.domain.consulting.service;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.consulting")
@Getter
@Setter
public class ConsultingSchedulerProperties {

    private int maxAttempts = 3;
    private int batchSize = 100;
    private Duration retryDelay = Duration.ofMinutes(5);
    private Duration leaseDuration = Duration.ofMinutes(4);

    public int safeMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public int safeBatchSize() {
        return Math.max(1, batchSize);
    }

    public Duration safeRetryDelay() {
        return positiveOrDefault(retryDelay, Duration.ofMinutes(5));
    }

    public Duration safeLeaseDuration() {
        return positiveOrDefault(leaseDuration, Duration.ofMinutes(4));
    }

    private Duration positiveOrDefault(Duration value, Duration defaultValue) {
        if (value == null || value.isZero() || value.isNegative()) {
            return defaultValue;
        }
        return value;
    }

}
