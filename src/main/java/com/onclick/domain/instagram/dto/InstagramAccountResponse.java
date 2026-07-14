package com.onclick.domain.instagram.dto;

import java.time.LocalDateTime;

import com.onclick.domain.instagram.entity.InstagramAccount;

public record InstagramAccountResponse(
        String accountId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InstagramAccountResponse from(InstagramAccount account) {
        return new InstagramAccountResponse(
                account.getAccountId(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
