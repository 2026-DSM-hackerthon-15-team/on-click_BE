package com.onclick.domain.instagram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstagramAccountUpsertRequest(
        @NotBlank @Size(max = 100) String accountId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(max = 255) String password
) {
    @Override
    public String toString() {
        return "InstagramAccountUpsertRequest[accountId=" + accountId
                + ", password=***]";
    }
}
