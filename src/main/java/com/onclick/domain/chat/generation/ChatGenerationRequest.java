package com.onclick.domain.chat.generation;

import java.util.List;

public record ChatGenerationRequest(
        Long storeId,
        Long chatRoomId,
        Long userMessageId,
        String message,
        List<ChatHistoryItem> history
) {

    public ChatGenerationRequest {
        history = List.copyOf(history);
    }
}
