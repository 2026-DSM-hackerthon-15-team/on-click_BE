package com.onclick.common.ai.dto;

import java.util.List;

public record InstagramPublishRequest(
        Long userId,
        String instagramUsername,
        String instagramPassword,
        String content,
        List<String> hashtags,
        List<InstagramImageAttachment> images,
        String idempotencyKey
) {
    public InstagramPublishRequest {
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
        images = images == null ? List.of() : List.copyOf(images);
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        requireLength(instagramUsername, "instagramUsername", 1, 100);
        requireLength(instagramPassword, "instagramPassword", 8, 200);
        requireLength(content, "content", 1, 2_200);
        if (hashtags.size() > 30 || hashtags.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("hashtags must contain at most 30 non-blank items");
        }
        if (images.isEmpty() || images.size() > 10
                || images.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("images must contain between 1 and 10 image attachments");
        }
        requireLength(idempotencyKey, "idempotencyKey", 1, 100);
    }

    private static void requireLength(String value, String field, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        if (length < min || length > max) {
            throw new IllegalArgumentException(
                    field + " must be between " + min + " and " + max + " characters"
            );
        }
    }

    @Override
    public String toString() {
        return "InstagramPublishRequest[userId=" + userId
                + ", instagramUsername=" + instagramUsername
                + ", instagramPassword=***, content=" + content
                + ", hashtags=" + hashtags
                + ", images=" + images
                + ", idempotencyKey=" + idempotencyKey + "]";
    }
}
