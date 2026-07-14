package com.onclick.common.ai.dto;

public record ConsultingGenerationResult(
        String title,
        String content
) {

    public ConsultingGenerationResult {
        title = requireText(title, "title");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
