package com.onclick.domain.chat.dto;

import java.time.LocalDateTime;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getClientMessageId(),
                message.getRole(),
                message.getStatus(),
                message.getStatus() == ChatMessageStatus.PENDING && message.getContent().isEmpty()
                        ? null
                        : message.getContent(),
                message.getRetryCount(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
