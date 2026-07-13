package com.onclick.domain.chat.dto;

import java.time.Instant;

import com.onclick.domain.chat.entity.ChatRoom;

public record ChatRoomResponse(
        Long id,
        Long storeId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public static ChatRoomResponse from(ChatRoom room) {
        return new ChatRoomResponse(
                room.getId(),
                room.getStoreId(),
                room.getTitle(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
