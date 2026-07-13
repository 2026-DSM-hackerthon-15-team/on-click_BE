package com.onclick.domain.auth.dto;

import java.time.Instant;

public record SignUpResponse(
        Long userId,
        String accountId,
        Long storeId,
        String storeName,
        Instant createdAt
) {
}
