package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.InstagramPublishRequest;
import com.onclick.common.ai.dto.InstagramPublishStatus;
import com.onclick.common.ai.dto.SaleTransactionInput;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.onclick.common.ai.dto.InstagramImageAttachment;
import static org.assertj.core.api.Assertions.assertThat;

class MockAiClientTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:30:00Z");
    private static final LocalDateTime NOW_KST = LocalDateTime.of(2026, 7, 13, 15, 30);

    private final MockAiClient mockAiClient = new MockAiClient(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsFixedClosingSalesMockWithoutCalculatingForecast() {
        ClosingSalesForecastResult result = mockAiClient.forecastClosingSales(
                new ClosingSalesForecastRequest(
                        1L,
                        LocalDateTime.of(2026, 7, 13, 15, 0),
                        salesData()
                )
        );

        assertThat(result.expectedClosingSales()).isEqualTo(MockAiClient.MOCK_CLOSING_SALES);
        assertThat(result.generatedAt()).isEqualTo(NOW_KST);
    }

    @Test
    void returnsFixedTomorrowVisitorsMockWithoutCalculatingForecast() {
        TomorrowVisitorsForecastResult result = mockAiClient.forecastTomorrowVisitors(
                new TomorrowVisitorsForecastRequest(1L, LocalDate.of(2026, 7, 13), salesData())
        );

        assertThat(result.expectedVisitors()).isEqualTo(MockAiClient.MOCK_TOMORROW_VISITORS);
        assertThat(result.generatedAt()).isEqualTo(NOW_KST);
    }

    @Test
    void returnsDeterministicConsultingChatAndMarketingMocks() {
        var consulting = mockAiClient.generateDailyConsulting(new ConsultingGenerationRequest(
                9L,
                1L,
                LocalDate.of(2026, 7, 13),
                ConsultingGenerationRequest.DAILY_V1
        ));
        var chat = mockAiClient.generateChatReply(new ChatGenerationRequest(
                9L,
                1L,
                2L,
                "오늘 매출을 알려줘",
                List.of("sales_analysis"),
                List.of()
        ));
        var marketing = mockAiClient.generateMarketing(new MarketingGenerationRequest(
                9L,
                List.of("https://cdn.example.com/menu.jpg"),
                "신메뉴 홍보",
                List.of("#신메뉴"),
                "친근하게",
                null
        ));
        var publication = mockAiClient.publishInstagram(
                31L,
                new InstagramPublishRequest(
                        9L,
                        "onclick_store",
                        "password123!",
                        "신메뉴 홍보",
                        List.of("#신메뉴"),
                        List.of(new InstagramImageAttachment(
                                "menu.jpg",
                                new byte[] {1, 2, 3},
                                "image/jpeg"
                        )),
                        "publish-key"
                ),
                "jwt"
        );

        assertThat(consulting.title()).isEqualTo("2026-07-13 일일 영업 컨설팅");
        assertThat(consulting.content()).contains("1번 매장", "2026-07-13 영업 데이터");
        assertThat(chat.content()).isEqualTo(MockAiClient.MOCK_CHAT_PREFIX + "오늘 매출을 알려줘");
        assertThat(marketing.content()).isEqualTo(
                MockAiClient.MOCK_MARKETING_PREFIX + "신메뉴 홍보"
        );
        assertThat(marketing.model()).isEqualTo(MockAiClient.MOCK_MARKETING_MODEL);
        assertThat(publication.status()).isEqualTo(InstagramPublishStatus.PUBLISHED);
        assertThat(publication.externalPostId()).isEqualTo("mock-instagram-31");
    }

    private List<SaleTransactionInput> salesData() {
        return List.of(new SaleTransactionInput(
                LocalDateTime.of(2026, 7, 13, 12, 0),
                10_000L,
                SaleTransactionInput.Status.COMPLETED
        ));
    }
}
