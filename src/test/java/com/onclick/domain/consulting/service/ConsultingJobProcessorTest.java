package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConsultingJobProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 22, 5);
    private static final Long STORE_ID = 3L;

    @Mock
    private ConsultingJobManager jobManager;

    @Mock
    private ConsultingRequestFactory requestFactory;

    @Mock
    private AiClient aiClient;

    private ConsultingJobProcessor processor;

    @BeforeEach
    void setUp() {
        ConsultingSchedulerProperties properties = new ConsultingSchedulerProperties();
        properties.setMaxAttempts(3);
        properties.setRetryDelay(Duration.ofMinutes(5));
        properties.setLeaseDuration(Duration.ofMinutes(2));
        processor = new ConsultingJobProcessor(
                jobManager,
                requestFactory,
                aiClient,
                properties,
                fixedClock(NOW)
        );
    }

    @Test
    void claimsAndCompletesGeneratedConsulting() {
        ConsultingJobClaim claim = claim();
        ConsultingGenerationRequest request = request(claim);
        ConsultingGenerationResult result = new ConsultingGenerationResult(
                "일일 분석",
                "저녁 매출이 좋습니다."
        );
        given(jobManager.claim(10L, NOW, Duration.ofMinutes(2), 3))
                .willReturn(Optional.of(claim));
        given(requestFactory.create(claim)).willReturn(request);
        given(aiClient.generateDailyConsulting(request)).willReturn(result);

        processor.process(10L);

        verify(jobManager).complete(claim, result, NOW);
    }

    @Test
    void persistsRetryMetadataWhenAiGenerationFails() {
        ConsultingJobClaim claim = claim();
        ConsultingGenerationRequest request = request(claim);
        given(jobManager.claim(10L, NOW, Duration.ofMinutes(2), 3))
                .willReturn(Optional.of(claim));
        given(requestFactory.create(claim)).willReturn(request);
        given(aiClient.generateDailyConsulting(request))
                .willThrow(new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        processor.process(10L);

        verify(jobManager).fail(
                eq(claim),
                eq(ErrorCode.AI_SERVICE_UNAVAILABLE.defaultMessage()),
                eq(NOW),
                eq(Duration.ofMinutes(5)),
                eq(3)
        );
    }

    @Test
    void skipsGenerationWhenAnotherDispatcherOrSchedulerAlreadyClaimedJob() {
        given(jobManager.claim(10L, NOW, Duration.ofMinutes(2), 3))
                .willReturn(Optional.empty());

        processor.process(10L);

        verifyNoInteractions(requestFactory, aiClient);
    }

    private ConsultingJobClaim claim() {
        return new ConsultingJobClaim(10L, STORE_ID, LocalDate.of(2026, 7, 13), 1);
    }

    private ConsultingGenerationRequest request(ConsultingJobClaim claim) {
        return new ConsultingGenerationRequest(
                9L,
                claim.storeId(),
                claim.targetDate(),
                ConsultingGenerationRequest.DAILY_V1
        );
    }

    private Clock fixedClock(LocalDateTime localDateTime) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return Clock.fixed(localDateTime.atZone(kst).toInstant(), kst);
    }
}
