package com.onclick.common.ai.dto;

import java.util.List;

public record ChatGenerationRequest(
        Long storeId,
        Long chatRoomId,
        Long userMessageId,
        String message,
        List<ChatHistoryMessage> history
) {

    public ChatGenerationRequest {
        history = List.copyOf(history);
    }
}
