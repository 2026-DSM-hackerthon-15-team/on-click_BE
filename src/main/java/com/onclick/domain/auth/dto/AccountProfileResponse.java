package com.onclick.domain.auth.dto;

import java.time.LocalDateTime;

import com.onclick.domain.auth.entity.User;

public record AccountProfileResponse(
        Long userId,
        String accountId,
        String name,
        String email,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
