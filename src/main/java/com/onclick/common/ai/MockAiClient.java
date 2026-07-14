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
    public ConsultingGenerationResult generateConsulting(ConsultingGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String title = request.targetDate() + " " + request.storeName() + " 영업 컨설팅";
        String content = "총매출 " + request.totalSalesAmount()
                + "원, 주문 " + request.orderCount()
                + "건, 판매수량 " + request.totalQuantity()
                + "개를 기준으로 생성한 컨설팅입니다.";
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
                MOCK_MARKETING_PREFIX + request.storeName() + " - " + request.prompt()
        );
    }

    private LocalDateTime now() {
        return KoreanTime.now(clock);
    }
}
