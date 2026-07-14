package com.onclick.domain.instagram.dto;

import com.onclick.domain.instagram.entity.InstagramAccount;

public record InstagramCredentialsResponse(
        String accountId,
        String password
) {
    public static InstagramCredentialsResponse from(InstagramAccount account) {
        return new InstagramCredentialsResponse(
                account.getAccountId(),
                account.revealPassword()
        );
    }

    @Override
    public String toString() {
        return "InstagramCredentialsResponse[accountId=" + accountId
                + ", password=***]";
    }
}
