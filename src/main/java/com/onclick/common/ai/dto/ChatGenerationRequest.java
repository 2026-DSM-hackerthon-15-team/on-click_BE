package com.onclick.common.ai.dto;

import java.util.List;

public record ChatGenerationRequest(
        Long userId,
        Long storeId,
        Long chatRoomId,
        String message,
        List<String> availableTools,
        List<String> attachmentKeys
) {

    public ChatGenerationRequest {
        availableTools = List.copyOf(availableTools);
        attachmentKeys = attachmentKeys == null ? List.of() : List.copyOf(attachmentKeys);
    }
}
