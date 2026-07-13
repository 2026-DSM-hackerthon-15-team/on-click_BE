package com.onclick.domain.marketing.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MarketingGenerateRequest(
        @NotBlank @Size(max = 100) String productName,
        @NotBlank @Size(max = 2000) String description,
        @PositiveOrZero Long price,
        @Size(max = 500) String promotion,
        @Size(max = 200) String targetAudience,
        @Size(max = 30) String tone,
        @Size(max = 1000) String additionalRequest,
        @Size(max = 10) List<Long> mediaIds
) {
}
