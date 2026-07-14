package com.onclick.domain.chat.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ChatGenerationResult;

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
                .willReturn(new ChatGenerationResult("AI 응답"));
        AiClientChatGenerationAdapter adapter = new AiClientChatGenerationAdapter(aiClient);

        String result = adapter.generate(new ChatGenerationRequest(
                9L,
                3L,
                10L,
                "현재 질문"
        ));

        assertThat(result).isEqualTo("AI 응답");
        assertThat(requestCaptor.getValue().userId()).isEqualTo(9L);
        assertThat(requestCaptor.getValue().storeId()).isEqualTo(3L);
        assertThat(requestCaptor.getValue().chatRoomId()).isEqualTo(10L);
        assertThat(requestCaptor.getValue().message()).isEqualTo("현재 질문");
        assertThat(requestCaptor.getValue().availableTools())
                .contains("sales_analysis", "marketing_generation");
        assertThat(requestCaptor.getValue().attachmentKeys()).isEmpty();
    }
}
