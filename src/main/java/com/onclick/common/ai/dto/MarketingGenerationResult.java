package com.onclick.common.ai.dto;

public record MarketingGenerationResult(
        String content,
        String model
) {

    public MarketingGenerationResult {
        content = requireText(content, "content");
        model = requireText(model, "model");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
