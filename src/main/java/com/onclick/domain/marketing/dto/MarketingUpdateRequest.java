package com.onclick.domain.marketing.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record MarketingUpdateRequest(
        @Size(max = 150) String title,
        @Size(max = 10000) String content,
        @Size(max = 30) List<@Size(max = 100) String> hashtags,
        @Size(max = 10) List<Long> mediaIds
) {
}
