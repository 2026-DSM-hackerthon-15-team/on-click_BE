package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    public static final long MOCK_CLOSING_SALES = 500_000L;
    public static final long MOCK_TOMORROW_VISITORS = 120L;

    private final Clock clock;

    public MockAiClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ClosingSalesForecastResult forecastClosingSales(ClosingSalesForecastRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new ClosingSalesForecastResult(MOCK_CLOSING_SALES, clock.instant());
    }

    @Override
    public TomorrowVisitorsForecastResult forecastTomorrowVisitors(TomorrowVisitorsForecastRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new TomorrowVisitorsForecastResult(MOCK_TOMORROW_VISITORS, clock.instant());
    }
}
