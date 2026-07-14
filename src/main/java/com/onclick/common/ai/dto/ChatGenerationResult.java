package com.onclick.common.ai.dto;

public record ChatGenerationResult(
        String content
) {

    public ChatGenerationResult {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        content = content.trim();
    }
}
