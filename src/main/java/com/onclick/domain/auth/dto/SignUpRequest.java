package com.onclick.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Size(max = 50) String accountId,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String storeName,
        @Size(max = 50) String timeZone
) {
}
