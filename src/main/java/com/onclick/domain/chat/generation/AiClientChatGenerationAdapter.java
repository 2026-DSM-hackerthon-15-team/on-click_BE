package com.onclick.domain.chat.generation;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ChatHistoryMessage;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AiClientChatGenerationAdapter implements ChatGenerationPort {

    private final AiClient aiClient;

    public AiClientChatGenerationAdapter(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public String generate(ChatGenerationRequest request) {
        var history = request.history().stream()
                .map(item -> new ChatHistoryMessage(item.role().name(), item.content()))
                .toList();
        var result = aiClient.generateChatReply(
                new com.onclick.common.ai.dto.ChatGenerationRequest(
                        request.storeId(),
                        request.chatRoomId(),
                        request.userMessageId(),
                        request.message(),
                        history
                )
        );
        return result == null ? null : result.content();
    }
}
