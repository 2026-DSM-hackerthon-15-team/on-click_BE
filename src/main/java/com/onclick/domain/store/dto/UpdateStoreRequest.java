package com.onclick.domain.store.dto;

import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(
        @Size(max = 100) String name,
        @Size(max = 50) String timeZone
) {
}
