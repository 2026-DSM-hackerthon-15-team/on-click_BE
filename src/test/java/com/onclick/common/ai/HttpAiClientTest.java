package com.onclick.common.ai;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
                .andExpect(jsonPath("$.businessDate").value("2026-07-13"))
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
    void postsTomorrowVisitorsForecastUsingIsoTargetDate() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/tomorrow-visitors"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-secret"))
                .andExpect(jsonPath("$.storeId").value(7))
                .andExpect(jsonPath("$.targetDate").value("2026-07-14"))
                .andRespond(withSuccess(
                        "{\"expectedVisitors\":120,\"generatedAt\":\"2026-07-13T13:00:00Z\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.forecastTomorrowVisitors(
                new TomorrowVisitorsForecastRequest(7L, LocalDate.of(2026, 7, 14))
        );

        assertThat(result.expectedVisitors()).isEqualTo(120);
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
    void postsDailyConsultingUsingDocumentedAiContract() {
        server.expect(once(), requestTo("https://ai.example.test/ai/consultings/daily"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-secret"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(9))
                .andExpect(jsonPath("$.storeId").value(3))
                .andExpect(jsonPath("$.targetDate").value("2026-07-13"))
                .andExpect(jsonPath("$.reportFormat").value("DAILY_V1"))
                .andExpect(jsonPath("$.consultingId").doesNotExist())
                .andExpect(jsonPath("$.salesData").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {
                          "title":"2026-07-13 일일 컨설팅",
                          "targetDate":"2026-07-13",
                          "summary":"평일 저녁 매출이 감소했습니다.",
                          "content":"## 오늘의 요약\\n평일 저녁 매출이 감소했습니다.",
                          "chatInsights":[],
                          "keyMetrics":[],
                          "externalFactors":[],
                          "estimatedCauses":[],
                          "recommendations":[],
                          "warnings":[],
                          "usedTools":[],
                          "citations":[],
                          "model":"test-model"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.generateDailyConsulting(new ConsultingGenerationRequest(
                9L,
                3L,
                LocalDate.of(2026, 7, 13),
                ConsultingGenerationRequest.DAILY_V1
        ));

        assertThat(result.title()).isEqualTo("2026-07-13 일일 컨설팅");
        assertThat(result.content()).startsWith("## 오늘의 요약");
        server.verify();
    }

    @Test
    void mapsMismatchedDailyConsultingDateToInvalidResponse() {
        server.expect(once(), requestTo("https://ai.example.test/ai/consultings/daily"))
                .andRespond(withSuccess(
                        """
                        {
                          "title":"일일 컨설팅",
                          "targetDate":"2026-07-12",
                          "summary":"요약",
                          "content":"본문"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.generateDailyConsulting(new ConsultingGenerationRequest(
                9L,
                3L,
                LocalDate.of(2026, 7, 13),
                ConsultingGenerationRequest.DAILY_V1
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void mapsIncompleteSuccessfulChatPayloadToInvalidResponse() {
        server.expect(once(), requestTo("https://ai.example.test/ai/chat"))
                .andRespond(withSuccess(
                        "{\"answer\":\"응답\",\"usedTools\":[]}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.generateChatReply(new ChatGenerationRequest(
                9L,
                3L,
                12L,
                "질문",
                List.of("sales_analysis"),
                List.of()
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void mapsInvalidSuccessfulAiPayloadToInvalidResponse() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withSuccess(
                        "{\"expectedClosingSales\":-1,\"generatedAt\":\"2026-07-13T13:00:00Z\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void postsMarketingCopyUsingDocumentedAiContract() {
        server.expect(once(), requestTo("https://ai.example.test/ai/marketings/copy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-secret"))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://cdn.example.com/menu.jpg"))
                .andExpect(jsonPath("$.draftText").value("신메뉴를 소개합니다."))
                .andExpect(jsonPath("$.tags[0]").value("신메뉴"))
                .andExpect(jsonPath("$.tone").value("친근하게"))
                .andExpect(jsonPath("$.additionalRequest").doesNotExist())
                .andRespond(withSuccess(
                        "{\"content\":\"완성된 문구\",\"model\":\"claude-sonnet-4-6\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.generateMarketing(marketingRequest());

        assertThat(result.content()).isEqualTo("완성된 문구");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
        server.verify();
    }

    @Test
    void mapsIncompleteMarketingResponseToInvalidResponse() {
        server.expect(once(), requestTo("https://ai.example.test/ai/marketings/copy"))
                .andRespond(withSuccess("{\"content\":\"완성된 문구\"}", MediaType.APPLICATION_JSON));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.generateMarketing(marketingRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void rejectsNonHttpsMarketingImageBeforeCallingAi() {
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());
        MarketingGenerationRequest request = new MarketingGenerationRequest(
                7L,
                List.of("http://localhost:8080/public/media/image"),
                "신메뉴를 소개합니다.",
                List.of("신메뉴"),
                null,
                null
        );

        assertThatThrownBy(() -> client.generateMarketing(request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_REQUEST_REJECTED));
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
    void rejectsInvalidHttpTimeoutAndPathConfiguration() {
        AiHttpProperties invalidTimeout = productionProperties();
        invalidTimeout.setReadTimeout(Duration.ZERO);
        assertThatThrownBy(() -> new HttpAiClient(invalidTimeout))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_READ_TIMEOUT");

        AiHttpProperties invalidPath = productionProperties();
        invalidPath.getPaths().setChat("ai/chat");
        assertThatThrownBy(() -> new HttpAiClient(invalidPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_CHAT_PATH");
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500})
    void retriesTransientStatusAndMapsFinalFailureToAiServiceUnavailable(int status) {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void doesNotRetryNonTransientClientFailure(int status) {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_REQUEST_REJECTED));
        server.verify();
    }

    @Test
    void retriesTransportTimeoutAndMapsFinalFailureToUnavailable() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void doesNotRetryMalformedSuccessfulJson() {
        server.expect(once(), requestTo("https://ai.example.test/ai/forecasts/closing-sales"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.forecastClosingSales(
                new ClosingSalesForecastRequest(7L, LocalDate.of(2026, 7, 13))
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    private MarketingGenerationRequest marketingRequest() {
        return new MarketingGenerationRequest(
                7L,
                List.of("https://cdn.example.com/menu.jpg"),
                "신메뉴를 소개합니다.",
                List.of("신메뉴"),
                "친근하게",
                null
        );
    }

    private AiHttpProperties productionProperties() {
        AiHttpProperties configured = new AiHttpProperties();
        configured.setBaseUrl("https://ai.example.test");
        configured.setInternalApiKey("internal-secret");
        return configured;
    }
}
