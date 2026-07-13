package com.onclick.domain.instagram.dto;

import java.time.Instant;

import com.onclick.domain.instagram.entity.InstagramIntegration;

public record InstagramStatusResponse(
        boolean connected,
        String instagramUserId,
        String username,
        Instant tokenExpiresAt,
        Instant connectedAt
) {
    public static InstagramStatusResponse disconnected() {
        return new InstagramStatusResponse(false, null, null, null, null);
    }

    public static InstagramStatusResponse from(InstagramIntegration integration) {
        return new InstagramStatusResponse(
                true,
                integration.getInstagramUserId(),
                integration.getUsername(),
                integration.getTokenExpiresAt(),
                integration.getConnectedAt()
        );
    }
}
