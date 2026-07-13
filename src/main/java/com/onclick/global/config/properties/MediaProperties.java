package com.onclick.global.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        String storageDirectory,
        String publicBaseUrl,
        long maxFileSizeBytes,
        Duration orphanRetention
) {
}
