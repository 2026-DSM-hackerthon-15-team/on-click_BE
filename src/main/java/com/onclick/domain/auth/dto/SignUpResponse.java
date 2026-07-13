package com.onclick.domain.auth.dto;

import java.time.Instant;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record SignUpResponse(
        Long userId,
        String accountId,
        String name,
        String email,
        Long storeId,
        String storeName,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        Instant createdAt
) {
}
