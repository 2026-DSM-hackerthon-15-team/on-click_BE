package com.onclick.domain.auth.dto;

public record AuthTokenResponse(
        Long userId,
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static AuthTokenResponse bearer(Long userId, String accessToken, long expiresInSeconds) {
        return new AuthTokenResponse(userId, accessToken, "Bearer", expiresInSeconds);
    }
}
