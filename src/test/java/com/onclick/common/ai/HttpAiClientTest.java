package com.onclick.common.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiClientTest {

    private AiHttpProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new AiHttpProperties();
        properties.setBaseUrl("https://ai.example.test");
        properties.setInternalApiKey("internal-secret");
        properties.setMaxAttempts(2);
        restClientBuilder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(properties.getInternalApiKeyHeader(), properties.getInternalApiKey());
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void postsToConfiguredPathWithInternalKeyAndDeserializesResponse() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-secret"))
                .andRespond(withSuccess(
                        "{\"expectedClosingSales\":750000,\"generatedAt\":\"2026-07-13T13:00:00Z\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        );

        assertThat(result.expectedClosingSales()).isEqualTo(750_000);
        assertThat(result.generatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 13, 22, 0));
        server.verify();
    }

    @Test
    void postsChatUsingDocumentedAiContractAndReadsAnswerWithoutGeneratedAt() {
        server.expect(once(), requestTo("https://ai.example.test/ai/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-secret"))
                .andExpect(jsonPath("$.userId").value(9))
                .andExpect(jsonPath("$.storeId").value(3))
                .andExpect(jsonPath("$.chatRoomId").value(12))
                .andExpect(jsonPath("$.message").value("이번 주 매출이 왜 줄었어?"))
                .andExpect(jsonPath("$.availableTools[0]").value("sales_analysis"))
                .andExpect(jsonPath("$.attachmentKeys").isArray())
                .andExpect(jsonPath("$.userMessageId").doesNotExist())
                .andExpect(jsonPath("$.history").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {
                          "answer":"평일 저녁 매출 하락이 가장 큰 원인입니다.",
                          "usedTools":[],
                          "citations":[],
                          "model":"test-model",
                          "finishReason":"STOP"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.generateChatReply(new ChatGenerationRequest(
                9L,
                3L,
                12L,
                "이번 주 매출이 왜 줄었어?",
                List.of("sales_analysis"),
                List.of()
        ));

        assertThat(result.content()).isEqualTo("평일 저녁 매출 하락이 가장 큰 원인입니다.");
        server.verify();
    }

    @Test
    void mapsInvalidSuccessfulAiPayloadToServiceUnavailable() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withSuccess(
                        "{\"expectedClosingSales\":-1,\"generatedAt\":\"2026-07-13T13:00:00Z\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void mapsBlankMarketingContentToServiceUnavailable() {
        server.expect(once(), requestTo("https://ai.example.test/ai/marketing/generations"))
                .andRespond(withSuccess("{\"content\":\"  \"}", MediaType.APPLICATION_JSON));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.generateMarketing(
                new MarketingGenerationRequest(3L, "강남점", "신메뉴 홍보")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void requiresInternalKeyWhenHttpProviderIsEnabled() {
        AiHttpProperties missingKey = new AiHttpProperties();
        missingKey.setBaseUrl("https://ai.example.test");
        missingKey.setInternalApiKey(" ");

        assertThatThrownBy(() -> new HttpAiClient(missingKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_INTERNAL_API_KEY");
    }

    @Test
    void retriesServerFailureAndMapsFinalFailureToAiServiceUnavailable() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withServerError());
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withServerError());
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void doesNotRetryNonTransientClientFailure() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withResourceNotFound());
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }
}
