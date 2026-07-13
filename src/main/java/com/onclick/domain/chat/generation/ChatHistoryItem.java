package com.onclick.domain.chat.generation;

import com.onclick.domain.chat.entity.ChatMessageRole;

public record ChatHistoryItem(
        ChatMessageRole role,
        String content
) {
}
