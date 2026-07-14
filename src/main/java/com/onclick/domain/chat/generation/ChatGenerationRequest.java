package com.onclick.domain.chat.generation;

public record ChatGenerationRequest(
        Long userId,
        Long storeId,
        Long chatRoomId,
        String message
) {
}
