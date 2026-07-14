package com.onclick.domain.chat.generation;

import com.onclick.common.ai.AiClient;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class AiClientChatGenerationAdapter implements ChatGenerationPort {

    static final List<String> AVAILABLE_TOOLS = List.of(
            "sales_analysis",
            "weather_search",
            "event_search",
            "legal_search",
            "marketing_generation"
    );

    private final AiClient aiClient;

    @Override
    public String generate(ChatGenerationRequest request) {
        var result = aiClient.generateChatReply(
                new com.onclick.common.ai.dto.ChatGenerationRequest(
                        request.userId(),
                        request.storeId(),
                        request.chatRoomId(),
                        request.message(),
                        AVAILABLE_TOOLS,
                        List.of()
                )
        );
        return result == null ? null : result.content();
    }
}
