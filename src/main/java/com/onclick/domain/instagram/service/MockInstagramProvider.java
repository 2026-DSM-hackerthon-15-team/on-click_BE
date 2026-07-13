package com.onclick.domain.instagram.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.onclick.global.config.properties.InstagramProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "app.instagram", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockInstagramProvider implements InstagramProvider {

    private final InstagramProperties properties;
    private final Clock clock;

    public MockInstagramProvider(InstagramProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.oauthCallbackUrl())
                .queryParam("code", "mock-code")
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    @Override
    public ConnectedAccount exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InstagramProviderException("Mock authorization code is missing", false);
        }
        Instant expiresAt = clock.instant().plus(Duration.ofDays(60));
        return new ConnectedAccount("mock-instagram-user", "onclick_mock", "mock-access-token", expiresAt);
    }

    @Override
    public RefreshedToken refresh(String accessToken) {
        return new RefreshedToken(accessToken + "-refreshed", clock.instant().plus(Duration.ofDays(60)));
    }

    @Override
    public PublishedPost publish(PublishRequest request) {
        if (request.imageUrls() == null || request.imageUrls().isEmpty()) {
            throw new InstagramProviderException("At least one image is required", false);
        }
        String postId = "mock-" + request.idempotencyKey();
        return new PublishedPost(postId, "https://www.instagram.com/p/" + postId, clock.instant());
    }
}
