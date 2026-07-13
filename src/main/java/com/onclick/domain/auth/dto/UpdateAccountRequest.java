package com.onclick.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @Size(max = 50) String accountId,
        @Size(max = 100) String name,
        @Email @Size(max = 255) String email
) {
}
