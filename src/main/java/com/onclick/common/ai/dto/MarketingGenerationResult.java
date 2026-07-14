package com.onclick.common.ai.dto;

public record MarketingGenerationResult(
        String content
) {

    public MarketingGenerationResult {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        content = content.trim();
    }
}
