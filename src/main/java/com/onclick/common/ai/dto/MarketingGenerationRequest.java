package com.onclick.common.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarketingGenerationRequest(
        Long userId,
        List<String> imageUrls,
        String draftText,
        List<String> tags,
        String tone,
        String additionalRequest
) {

    public MarketingGenerationRequest {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (imageUrls == null || imageUrls.isEmpty() || imageUrls.size() > 10) {
            throw new IllegalArgumentException("imageUrls must contain between 1 and 10 items");
        }
        imageUrls = imageUrls.stream()
                .map(value -> requireText(value, "imageUrls"))
                .toList();
        draftText = requireText(draftText, "draftText");
        if (draftText.length() > 2000) {
            throw new IllegalArgumentException("draftText must not exceed 2000 characters");
        }
        if (tags != null) {
            if (tags.size() > 30) {
                throw new IllegalArgumentException("tags must not contain more than 30 items");
            }
            tags = tags.stream()
                    .map(value -> requireText(value, "tags"))
                    .toList();
        }
        tone = normalizeOptional(tone, 100, "tone");
        additionalRequest = normalizeOptional(additionalRequest, 500, "additionalRequest");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
