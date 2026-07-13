package com.onclick.common.ai.dto;

public record ChatHistoryMessage(
        String role,
        String content
) {
}
