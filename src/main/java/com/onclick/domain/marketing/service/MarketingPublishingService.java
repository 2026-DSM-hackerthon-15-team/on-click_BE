package com.onclick.domain.marketing.service;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.InstagramPublishResult;
import com.onclick.domain.marketing.dto.MarketingResponse;
import com.onclick.global.error.ApiException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketingPublishingService {

    private static final String UNCERTAIN_FAILURE_REASON =
            "Instagram 게시 결과를 확인하지 못했습니다. 재승인 전에 Instagram에서 게시 여부를 확인해 주세요.";

    private final MarketingPublicationTransactions transactions;
    private final MarketingService marketingService;
    private final AiClient aiClient;

    public MarketingResponse approveAndPublish(Jwt jwt, Long storeId, Long marketingId) {
        MarketingPublicationTransactions.PreparedPublication prepared =
                transactions.prepare(jwt, storeId, marketingId);
        try {
            InstagramPublishResult result = aiClient.publishInstagram(
                    prepared.marketingId(),
                    prepared.request(),
                    jwt.getTokenValue()
            );
            transactions.complete(prepared.marketingId(), result);
        } catch (ApiException exception) {
            transactions.fail(prepared.marketingId(), failureReason(exception));
            throw exception;
        } catch (RuntimeException exception) {
            transactions.fail(prepared.marketingId(), UNCERTAIN_FAILURE_REASON);
            throw exception;
        }
        return marketingService.findOne(jwt, storeId, marketingId);
    }

    private String failureReason(ApiException exception) {
        return switch (exception.errorCode()) {
            case INSTAGRAM_CREDENTIALS_INVALID, INSTAGRAM_LOGIN_CHALLENGE_REQUIRED,
                    INVALID_INSTAGRAM_POST, DUPLICATE_PUBLISH_REQUEST -> exception.getMessage();
            default -> UNCERTAIN_FAILURE_REASON;
        };
    }
}
