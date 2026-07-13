package com.onclick.global.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.instagram")
public record InstagramProperties(
        String provider,
        String clientId,
        String clientSecret,
        String oauthCallbackUrl,
        String frontendRedirectUrl,
        String authorizeUrl,
        String tokenUrl,
        String graphBaseUrl,
        String scopes,
        String tokenEncryptionKey,
        Duration stateTtl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
