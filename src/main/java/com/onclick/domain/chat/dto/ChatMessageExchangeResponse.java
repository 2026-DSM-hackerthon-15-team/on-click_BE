package com.onclick.domain.chat.dto;

public record ChatMessageExchangeResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage
) {
}
