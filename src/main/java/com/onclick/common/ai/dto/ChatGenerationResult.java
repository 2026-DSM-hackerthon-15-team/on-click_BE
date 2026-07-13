package com.onclick.common.ai.dto;

import java.time.Instant;

public record ChatGenerationResult(
        String content,
        Instant generatedAt
) {
}
