package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultingSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T13:05:00Z");
    private static final Long STORE_ID = 3L;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ConsultingJobManager jobManager;

    @Mock
    private ConsultingRequestFactory requestFactory;

    @Mock
    private AiClient aiClient;

    private ConsultingSchedulerProperties properties;
    private ConsultingScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new ConsultingSchedulerProperties();
        properties.setMaxAttempts(3);
        properties.setRetryDelay(Duration.ofMinutes(5));
        properties.setLeaseDuration(Duration.ofMinutes(2));
        scheduler = new ConsultingScheduler(
                storeRepository,
                jobManager,
                requestFactory,
                aiClient,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsCurrentBusinessDateAfterClosingAndCompletesClaimedJob() {
        Store store = store();
        ConsultingJobClaim claim = new ConsultingJobClaim(
                10L,
                STORE_ID,
                LocalDate.of(2026, 7, 13),
                1
        );
        ConsultingGenerationRequest request = request(claim);
        ConsultingGenerationResult result = new ConsultingGenerationResult(
                "일일 분석",
                "저녁 매출이 좋습니다.",
                NOW
        );
        given(storeRepository.findAll()).willReturn(List.of(store));
        given(jobManager.findRetryableIds(NOW, 3, 100)).willReturn(List.of(10L));
        given(jobManager.claim(10L, NOW, Duration.ofMinutes(2), 3))
                .willReturn(Optional.of(claim));
        given(requestFactory.create(claim)).willReturn(request);
        given(aiClient.generateConsulting(request)).willReturn(result);

        scheduler.generateDueConsultings();

        verify(jobManager).createPending(STORE_ID, LocalDate.of(2026, 7, 13), NOW);
        verify(jobManager).complete(claim, result, NOW);
    }

    @Test
    void persistsRetryMetadataWhenAiGenerationFails() {
        Store store = store();
        ConsultingJobClaim claim = new ConsultingJobClaim(
                10L,
                STORE_ID,
                LocalDate.of(2026, 7, 13),
                1
        );
        ConsultingGenerationRequest request = request(claim);
        given(storeRepository.findAll()).willReturn(List.of(store));
        given(jobManager.findRetryableIds(NOW, 3, 100)).willReturn(List.of(10L));
        given(jobManager.claim(10L, NOW, Duration.ofMinutes(2), 3))
                .willReturn(Optional.of(claim));
        given(requestFactory.create(claim)).willReturn(request);
        given(aiClient.generateConsulting(request))
                .willThrow(new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        scheduler.generateDueConsultings();

        verify(jobManager).fail(
                eq(claim),
                eq(ErrorCode.AI_SERVICE_UNAVAILABLE.defaultMessage()),
                eq(NOW),
                eq(Duration.ofMinutes(5)),
                eq(3)
        );
    }

    private Store store() {
        Store store = new Store(new User("owner", "hash"), "강남점", "Asia/Seoul");
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        ReflectionTestUtils.setField(store, "createdAt", Instant.parse("2026-07-01T00:00:00Z"));
        return store;
    }

    private ConsultingGenerationRequest request(ConsultingJobClaim claim) {
        return new ConsultingGenerationRequest(
                claim.consultingId(),
                claim.storeId(),
                "강남점",
                claim.targetDate(),
                "Asia/Seoul",
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
