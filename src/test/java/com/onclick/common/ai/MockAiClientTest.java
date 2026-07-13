package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiClientTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:30:00Z");

    private final MockAiClient mockAiClient = new MockAiClient(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsFixedClosingSalesMockWithoutCalculatingForecast() {
        ClosingSalesForecastResult result = mockAiClient.forecastClosingSales(
                new ClosingSalesForecastRequest(1L, LocalDate.of(2026, 7, 13))
        );

        assertThat(result.expectedClosingSales()).isEqualTo(MockAiClient.MOCK_CLOSING_SALES);
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsFixedTomorrowVisitorsMockWithoutCalculatingForecast() {
        TomorrowVisitorsForecastResult result = mockAiClient.forecastTomorrowVisitors(
                new TomorrowVisitorsForecastRequest(1L, LocalDate.of(2026, 7, 14))
        );

        assertThat(result.expectedVisitors()).isEqualTo(MockAiClient.MOCK_TOMORROW_VISITORS);
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsDeterministicConsultingChatAndMarketingMocks() {
        var consulting = mockAiClient.generateConsulting(new ConsultingGenerationRequest(
                11L,
                1L,
                "강남점",
                LocalDate.of(2026, 7, 13),
                "Asia/Seoul",
                50_000,
                3,
                3,
                7,
                List.of(),
                List.of()
        ));
        var chat = mockAiClient.generateChatReply(new ChatGenerationRequest(
                1L,
                2L,
                3L,
                "오늘 매출을 알려줘",
                List.of()
        ));
        var marketing = mockAiClient.generateMarketing(new MarketingGenerationRequest(
                1L,
                "강남점",
                "신메뉴 홍보"
        ));

        assertThat(consulting.title()).isEqualTo("2026-07-13 강남점 영업 컨설팅");
        assertThat(consulting.content()).contains("총매출 50000원", "주문 3건", "판매수량 7개");
        assertThat(consulting.generatedAt()).isEqualTo(NOW);
        assertThat(chat.content()).isEqualTo(MockAiClient.MOCK_CHAT_PREFIX + "오늘 매출을 알려줘");
        assertThat(chat.generatedAt()).isEqualTo(NOW);
        assertThat(marketing.content()).isEqualTo(
                MockAiClient.MOCK_MARKETING_PREFIX + "강남점 - 신메뉴 홍보"
        );
        assertThat(marketing.generatedAt()).isEqualTo(NOW);
    }
}
