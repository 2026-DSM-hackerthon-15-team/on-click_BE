package com.onclick.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.onclick.domain.chat.entity.ChatRoom;

public record ChatRoomDetailResponse(
        Long id,
        Long storeId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ChatMessageResponse> messages
) {

    public static ChatRoomDetailResponse from(
            ChatRoom room,
            List<ChatMessageResponse> messages
    ) {
        return new ChatRoomDetailResponse(
                room.getId(),
                room.getStoreId(),
                room.getTitle(),
                room.getCreatedAt(),
                room.getUpdatedAt(),
                List.copyOf(messages)
        );
    }
}
