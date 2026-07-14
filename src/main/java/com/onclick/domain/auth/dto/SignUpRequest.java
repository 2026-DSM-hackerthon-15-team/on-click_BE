package com.onclick.domain.auth.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.onclick.domain.store.entity.Industry;
import com.onclick.global.config.StrictLocalTimeDeserializer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Size(max = 50) String accountId,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String storeName,
        Industry industry,
        @Size(max = 255) String roadAddress,
        @JsonDeserialize(using = StrictLocalTimeDeserializer.class) LocalTime closingTime
) {

    public SignUpRequest(
            String accountId,
            String password,
            String name,
            String email,
            String storeName,
            LocalTime closingTime
    ) {
        this(accountId, password, name, email, storeName, null, null, closingTime);
    }
}
