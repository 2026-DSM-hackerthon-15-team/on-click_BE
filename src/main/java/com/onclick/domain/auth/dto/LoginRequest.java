package com.onclick.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String accountId,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
