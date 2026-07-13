package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
}
