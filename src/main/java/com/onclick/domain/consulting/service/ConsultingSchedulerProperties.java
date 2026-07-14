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
    private Duration leaseDuration = Duration.ofMinutes(2);

}
