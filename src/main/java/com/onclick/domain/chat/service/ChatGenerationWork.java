package com.onclick.domain.chat.service;

import com.onclick.domain.chat.generation.ChatGenerationRequest;

record ChatGenerationWork(
        Long assistantMessageId,
        int attempt,
        ChatGenerationRequest request
) {
}
