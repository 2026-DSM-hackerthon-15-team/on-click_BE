package com.onclick.domain.chat.service;

import org.springframework.stereotype.Component;

@Component
class ChatResponseComposer {

    String normalizeGeneratedResponse(String generatedContent) {
        String normalized = generatedContent == null ? "" : generatedContent.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Chat generator returned an empty response");
        }
        return normalized;
    }
}
