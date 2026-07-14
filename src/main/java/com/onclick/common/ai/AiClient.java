package com.onclick.common.ai;

import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ChatGenerationResult;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.common.ai.dto.InstagramPublishRequest;
import com.onclick.common.ai.dto.InstagramPublishResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;

public interface AiClient {

    ClosingSalesForecastResult forecastClosingSales(ClosingSalesForecastRequest request);

    TomorrowVisitorsForecastResult forecastTomorrowVisitors(TomorrowVisitorsForecastRequest request);

    ConsultingGenerationResult generateDailyConsulting(ConsultingGenerationRequest request);

    ChatGenerationResult generateChatReply(ChatGenerationRequest request);

    MarketingGenerationResult generateMarketing(MarketingGenerationRequest request);

    InstagramPublishResult publishInstagram(
            Long marketingId,
            InstagramPublishRequest request,
            String bearerToken
    );
}
