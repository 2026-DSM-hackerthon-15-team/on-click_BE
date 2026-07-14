package com.onclick.common.ai;

import java.time.LocalDateTime;
import java.util.List;

import com.onclick.common.ai.dto.InstagramPublishRequest;
import com.onclick.common.ai.dto.InstagramPublishStatus;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.SaleTransactionInput;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class HttpAiClientTest {

    private AiHttpProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new AiHttpProperties();
        properties.setBaseUrl("https://ai.example.test");
        properties.setMaxAttempts(3);
        restClientBuilder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void forecastsClosingSalesWithInternalApiKeyAndSecondPrecisionPayload() {
        server.expect(once(), requestTo(
                        "https://ai.example.test/ai/forecasts/closing-sales"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                
                .andExpect(content().json("""
                        {
                          "storeId": 5,
                          "asOf": "2026-07-14T18:00:00",
                          "salesData": [
                            {
                              "soldAt": "2026-07-14T12:10:00",
                              "totalPaidAmount": 18500,
                              "status": "COMPLETED"
                            },
                            {
                              "soldAt": "2026-07-14T13:20:00",
                              "totalPaidAmount": 9000,
                              "status": "CANCELLED"
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess(
                        """
                        {
                          "storeId":5,
                          "businessDate":"2026-07-14",
                          "currency":"KRW",
                          "observedSalesAmount":27500,
                          "forecastClosingSalesAmount":41000,
                          "model":"test-model",
                          "sampleDays":14,
                          "generatedAt":"2026-07-14T18:30:00"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.forecastClosingSales(closingSalesRequest());

        assertThat(result.expectedClosingSales()).isEqualTo(41_000);
        assertThat(result.generatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 18, 30));
        server.verify();
    }

    @Test
    void publishesInstagramWithApprovingUsersBearerTokenAndExactContract(CapturedOutput output) {
        server.expect(once(), requestTo(
                        "https://ai.example.test/ai/marketings/31/publish/instagram"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header("Authorization", "Bearer approving-user-jwt"))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.instagramUsername").value("onclick_store"))
                .andExpect(jsonPath("$.instagramPassword").value("password123!"))
                .andExpect(jsonPath("$.content").value("오늘의 신메뉴입니다."))
                .andExpect(jsonPath("$.hashtags[0]").value("#신메뉴"))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://cdn.example.com/menu.jpg"))
                .andExpect(jsonPath("$.idempotencyKey").value("publish-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "marketingId":31,
                          "platform":"INSTAGRAM",
                          "status":"PUBLISHED",
                          "externalPostId":"post-31",
                          "publishedUrl":"https://www.instagram.com/p/post-31",
                          "publishedAt":"2026-07-14T18:30:00",
                          "failureReason":null
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        var result = client.publishInstagram(31L, publishRequest(), "approving-user-jwt");

        assertThat(result.status()).isEqualTo(InstagramPublishStatus.PUBLISHED);
        assertThat(result.externalPostId()).isEqualTo("post-31");
        assertThat(result.publishedAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 18, 30));
        assertThat(output)
                .contains("AI Instagram publish started")
                .contains("request=InstagramPublishRequest[userId=7")
                .contains("instagramPassword=***")
                .contains("AI Instagram response received")
                .contains("response=InstagramPublishResult[marketingId=31")
                .doesNotContain("password123!")
                .doesNotContain("approving-user-jwt");
        server.verify();
    }

    @Test
    void logsAiRequestAndResponsePayloads(CapturedOutput output) {
        server.expect(once(), requestTo("https://ai.example.test/ai/marketings/copy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                
                .andRespond(withSuccess(
                        "{\"content\":\"완성된 문구\",\"model\":\"test-model\"}",
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());
        MarketingGenerationRequest request = new MarketingGenerationRequest(
                7L,
                List.of("https://cdn.example.com/menu.jpg"),
                "신메뉴를 소개합니다.",
                List.of("신메뉴"),
                "친근하게",
                null
        );

        client.generateMarketing(request);

        assertThat(output)
                .contains("AI request started: path=/ai/marketings/copy, attempt=1/3")
                .contains("request=MarketingGenerationRequest[userId=7")
                .contains("AI response received: path=/ai/marketings/copy, attempt=1/3")
                .contains("response=MarketingGenerationWireResponse[content=완성된 문구, model=test-model]");
        server.verify();
    }

    @Test
    void logsUpstreamResponseBodyWhenTheAiServerRejectsTheRequest(CapturedOutput output) {
        server.expect(once(), requestTo("https://ai.example.test/ai/marketings/copy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"errorCode\":\"INVALID_PROMPT\",\"message\":\"bad request\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());
        MarketingGenerationRequest request = new MarketingGenerationRequest(
                7L,
                List.of("https://cdn.example.com/menu.jpg"),
                "신메뉴를 소개합니다.",
                List.of("신메뉴"),
                "친근하게",
                null
        );

        assertThatThrownBy(() -> client.generateMarketing(request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_REQUEST_REJECTED));

        assertThat(output)
                .contains("AI request failed: path=/ai/marketings/copy, upstreamStatus=400")
                .contains("upstreamBody={\"errorCode\":\"INVALID_PROMPT\",\"message\":\"bad request\"}");
        server.verify();
    }

    @Test
    void rejectsProcessingBecauseTheSynchronousContractHasNoCompletionChannel() {
        server.expect(once(), requestTo(
                        "https://ai.example.test/ai/marketings/31/publish/instagram"))
                .andRespond(withSuccess(
                        """
                        {"marketingId":31,"platform":"INSTAGRAM","status":"PROCESSING"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.publishInstagram(31L, publishRequest(), "jwt"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void doesNotRetryInstagramPublishSideEffect() {
        server.expect(once(), requestTo(
                        "https://ai.example.test/ai/marketings/31/publish/instagram"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.publishInstagram(31L, publishRequest(), "jwt"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BROWSER_MCP_UNAVAILABLE));
        server.verify();
    }

    @Test
    void mapsInstagramChallengeToDedicatedError() {
        server.expect(once(), requestTo(
                        "https://ai.example.test/ai/marketings/31/publish/instagram"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"errorCode\":\"INSTAGRAM_LOGIN_CHALLENGE_REQUIRED\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        HttpAiClient client = new HttpAiClient(properties, restClientBuilder.build());

        assertThatThrownBy(() -> client.publishInstagram(31L, publishRequest(), "jwt"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INSTAGRAM_LOGIN_CHALLENGE_REQUIRED));
        server.verify();
    }

    private InstagramPublishRequest publishRequest() {
        return new InstagramPublishRequest(
                7L,
                "onclick_store",
                "password123!",
                "오늘의 신메뉴입니다.",
                List.of("#신메뉴"),
                List.of("https://cdn.example.com/menu.jpg"),
                "publish-key"
        );
    }

    private ClosingSalesForecastRequest closingSalesRequest() {
        return new ClosingSalesForecastRequest(
                5L,
                LocalDateTime.parse("2026-07-14T18:00:00.123456789"),
                List.of(
                        new SaleTransactionInput(
                                LocalDateTime.parse("2026-07-14T12:10:00.123456789"),
                                18_500,
                                SaleTransactionInput.Status.COMPLETED
                        ),
                        new SaleTransactionInput(
                                LocalDateTime.parse("2026-07-14T13:20:00.999999999"),
                                9_000,
                                SaleTransactionInput.Status.CANCELLED
                        )
                )
        );
    }
}
