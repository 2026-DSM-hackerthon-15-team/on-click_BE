package com.onclick.domain.visitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record HourlyVisitorUpsertRequest(
        @NotNull LocalDate businessDate,
        @NotNull @Min(0) @Max(23) Integer hour,
        @NotNull @PositiveOrZero Long visitorCount
) {
}
