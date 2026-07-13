package com.onclick.domain.chat.dto;

import java.time.Instant;

import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;

public record ChatMessageResponse(
        Long id,
        Long chatRoomId,
        String clientMessageId,
        ChatMessageRole role,
        ChatMessageStatus status,
        String content,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getClientMessageId(),
                message.getRole(),
                message.getStatus(),
                message.getContent(),
                message.getRetryCount(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
