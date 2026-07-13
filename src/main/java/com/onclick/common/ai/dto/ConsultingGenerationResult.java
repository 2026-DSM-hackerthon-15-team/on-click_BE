package com.onclick.common.ai.dto;

import java.time.Instant;

public record ConsultingGenerationResult(
        String title,
        String content,
        Instant generatedAt
) {
}
