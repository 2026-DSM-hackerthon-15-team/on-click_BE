package com.onclick.domain.instagram.dto;

import java.time.Instant;

public record InstagramConnectResponse(String authorizationUrl, Instant expiresAt) {
}
