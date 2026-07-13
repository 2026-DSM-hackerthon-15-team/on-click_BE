package com.onclick.common.ai;

import java.time.Instant;
import java.time.LocalDate;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
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
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-07-13T13:00:00Z"));
        server.verify();
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
