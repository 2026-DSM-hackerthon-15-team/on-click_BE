package com.onclick.common.ai.dto;

import java.time.Instant;

public record MarketingGenerationResult(
        String content,
        Instant generatedAt
) {
}
