package com.onclick.domain.instagram.service;

import java.time.Instant;
import java.util.List;

public interface InstagramProvider {

    String authorizationUrl(String state);

    ConnectedAccount exchangeCode(String code);

    RefreshedToken refresh(String accessToken);

    PublishedPost publish(PublishRequest request);

    default void disconnect(String accessToken) {
    }

    record ConnectedAccount(
            String instagramUserId,
            String username,
            String accessToken,
            Instant expiresAt
    ) {
    }

    record RefreshedToken(String accessToken, Instant expiresAt) {
    }

    record PublishRequest(
            String instagramUserId,
            String accessToken,
            String caption,
            List<String> imageUrls,
            String idempotencyKey
    ) {
    }

    record PublishedPost(String externalPostId, String permalink, Instant publishedAt) {
    }
}
