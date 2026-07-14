package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ChatGenerationResult;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import com.onclick.common.time.KoreanTime;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
public class MockAiClient implements AiClient {

    public static final long MOCK_CLOSING_SALES = 500_000L;
    public static final long MOCK_TOMORROW_VISITORS = 120L;
    public static final String MOCK_CHAT_PREFIX = "AI 답변: ";
    public static final String MOCK_MARKETING_PREFIX = "오늘의 ON:CLICK 추천: ";
    public static final String MOCK_MARKETING_MODEL = "mock-marketing-v1";

    private final Clock clock;

    @Override
    public ClosingSalesForecastResult forecastClosingSales(ClosingSalesForecastRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new ClosingSalesForecastResult(MOCK_CLOSING_SALES, now());
    }

    @Override
    public TomorrowVisitorsForecastResult forecastTomorrowVisitors(TomorrowVisitorsForecastRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new TomorrowVisitorsForecastResult(MOCK_TOMORROW_VISITORS, now());
    }

    @Override
    public ConsultingGenerationResult generateDailyConsulting(ConsultingGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String title = request.targetDate() + " 일일 영업 컨설팅";
        String content = request.storeId() + "번 매장의 "
                + request.targetDate() + " 영업 데이터를 기준으로 생성한 컨설팅입니다.";
        return new ConsultingGenerationResult(title, content);
    }

    @Override
    public ChatGenerationResult generateChatReply(ChatGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new ChatGenerationResult(MOCK_CHAT_PREFIX + request.message());
    }

    @Override
    public MarketingGenerationResult generateMarketing(MarketingGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new MarketingGenerationResult(
                MOCK_MARKETING_PREFIX + request.draftText(),
                MOCK_MARKETING_MODEL
        );
    }

    private LocalDateTime now() {
        return KoreanTime.now(clock);
    }
}
