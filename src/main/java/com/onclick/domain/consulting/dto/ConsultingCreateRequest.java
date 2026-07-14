package com.onclick.domain.consulting.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record ConsultingCreateRequest(
        @NotNull LocalDate targetDate
) {
}
