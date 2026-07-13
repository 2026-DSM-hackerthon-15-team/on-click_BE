package com.onclick.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageCreateRequest(
        @Size(max = 100) String clientMessageId,
        @NotBlank @Size(max = 4_000) String content
) {
    public ChatMessageCreateRequest(String content) {
        this(null, content);
    }
}
