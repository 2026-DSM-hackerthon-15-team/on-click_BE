package com.onclick.domain.auth.dto;

import java.time.Instant;

import com.onclick.domain.auth.entity.User;

public record AccountProfileResponse(
        Long userId,
        String accountId,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt
) {

    public static AccountProfileResponse from(User user) {
        return new AccountProfileResponse(
                user.getId(),
                user.getAccountId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
