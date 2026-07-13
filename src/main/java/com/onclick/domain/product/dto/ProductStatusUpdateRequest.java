package com.onclick.domain.product.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(@NotNull Boolean active) {
}
