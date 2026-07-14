package com.onclick.domain.auth.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.onclick.domain.store.entity.Industry;

public record SignUpResponse(
        Long userId,
        String accountId,
        String name,
        String email,
        Long storeId,
        String storeName,
        Industry industry,
        String roadAddress,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        LocalDateTime createdAt
) {
}
