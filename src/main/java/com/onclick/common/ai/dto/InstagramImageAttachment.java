package com.onclick.common.ai.dto;

import java.util.Objects;

public record InstagramImageAttachment(String filename, byte[] content, String contentType) {
    public InstagramImageAttachment {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
    }
}
