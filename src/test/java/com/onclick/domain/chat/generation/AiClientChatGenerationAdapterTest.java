package com.onclick.domain.chat.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ChatGenerationResult;
import com.onclick.domain.chat.entity.ChatMessageRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiClientChatGenerationAdapterTest {

    @Mock
    private AiClient aiClient;

    @Captor
    private ArgumentCaptor<com.onclick.common.ai.dto.ChatGenerationRequest> requestCaptor;

    @Test
    void mapsDomainRequestToCommonAiContract() {
        given(aiClient.generateChatReply(requestCaptor.capture()))
                .willReturn(new ChatGenerationResult("AI 응답", Instant.parse("2026-07-14T12:00:00Z")));
        AiClientChatGenerationAdapter adapter = new AiClientChatGenerationAdapter(aiClient);

        String result = adapter.generate(new ChatGenerationRequest(
                3L,
                10L,
                100L,
                "현재 질문",
                List.of(new ChatHistoryItem(ChatMessageRole.USER, "이전 질문"))
        ));

        assertThat(result).isEqualTo("AI 응답");
        assertThat(requestCaptor.getValue().storeId()).isEqualTo(3L);
        assertThat(requestCaptor.getValue().history()).singleElement().satisfies(history -> {
            assertThat(history.role()).isEqualTo("USER");
            assertThat(history.content()).isEqualTo("이전 질문");
        });
    }
}
