package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.consulting.TestFieldSetter;
import com.onclick.domain.consulting.dto.ConsultingCreateRequest;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultingServiceTest {

    private static final Long STORE_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 22, 0);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private ConsultingRepository consultingRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private ConsultingJobManager jobManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Jwt jwt;

    private ConsultingService consultingService;

    @BeforeEach
    void setUp() {
        consultingService = serviceAt(NOW);
    }

    @Test
    void enqueuesNewConsultingAfterValidatingStoreAndReturnsPendingResource() {
        Store store = store(LocalDateTime.of(2026, 7, 1, 9, 0));
        Consulting pending = pendingConsulting(10L);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(store);
        given(jobManager.createPending(STORE_ID, TARGET_DATE, NOW))
                .willReturn(new ConsultingJobRegistration(10L, true));
        given(consultingRepository.findByIdAndStoreId(10L, STORE_ID))
                .willReturn(Optional.of(pending));

        ConsultingCreationResult result = consultingService.generate(
                jwt,
                STORE_ID,
                new ConsultingCreateRequest(TARGET_DATE)
        );

        assertThat(result.created()).isTrue();
        assertThat(result.consulting().consultingId()).isEqualTo(10L);
        assertThat(result.consulting().status().name()).isEqualTo("PENDING");
        verify(eventPublisher).publishEvent(new ConsultingGenerationRequestedEvent(10L));
    }

    @Test
    void repeatedRequestReturnsExistingConsultingWithoutDispatchingAnotherJob() {
        Store store = store(LocalDateTime.of(2026, 7, 1, 9, 0));
        Consulting completed = completedConsulting(10L);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(store);
        given(jobManager.createPending(STORE_ID, TARGET_DATE, NOW))
                .willReturn(new ConsultingJobRegistration(10L, false));
        given(consultingRepository.findByIdAndStoreId(10L, STORE_ID))
                .willReturn(Optional.of(completed));

        ConsultingCreationResult result = consultingService.generate(
                jwt,
                STORE_ID,
                new ConsultingCreateRequest(TARGET_DATE)
        );

        assertThat(result.created()).isFalse();
        assertThat(result.consulting().status().name()).isEqualTo("COMPLETED");
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uniqueConstraintRaceWithSchedulerReturnsWinningResourceIdempotently() {
        Store store = store(LocalDateTime.of(2026, 7, 1, 9, 0));
        Consulting raced = pendingConsulting(11L);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(store);
        given(jobManager.createPending(STORE_ID, TARGET_DATE, NOW))
                .willThrow(new DataIntegrityViolationException("unique"));
        given(consultingRepository.findByStoreIdAndTargetDate(STORE_ID, TARGET_DATE))
                .willReturn(Optional.of(raced));
        given(consultingRepository.findByIdAndStoreId(11L, STORE_ID))
                .willReturn(Optional.of(raced));

        ConsultingCreationResult result = consultingService.generate(
                jwt,
                STORE_ID,
                new ConsultingCreateRequest(TARGET_DATE)
        );

        assertThat(result.created()).isFalse();
        assertThat(result.consulting().consultingId()).isEqualTo(11L);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsCurrentDateBeforeClosing() {
        Store store = store(LocalDateTime.of(2026, 7, 1, 9, 0));
        LocalDateTime beforeClosingTime = LocalDateTime.of(2026, 7, 13, 21, 59, 59);
        ConsultingService beforeClosing = serviceAt(beforeClosingTime);
        Consulting pending = pendingConsulting(12L);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(store);
        given(jobManager.createPending(STORE_ID, TARGET_DATE, beforeClosingTime))
                .willReturn(new ConsultingJobRegistration(12L, true));
        given(consultingRepository.findByIdAndStoreId(12L, STORE_ID))
                .willReturn(Optional.of(pending));

        ConsultingCreationResult result = beforeClosing.generate(
                jwt,
                STORE_ID,
                new ConsultingCreateRequest(TARGET_DATE)
        );

        assertThat(result.created()).isTrue();
        assertThat(result.consulting().consultingId()).isEqualTo(12L);
        verify(eventPublisher).publishEvent(new ConsultingGenerationRequestedEvent(12L));
    }

    @Test
    void acceptsDateBeforeStoreCreationInKst() {
        Store store = store(LocalDateTime.of(2026, 7, 13, 0, 30));
        Consulting pending = pendingConsulting(13L);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(store);
        given(jobManager.createPending(STORE_ID, LocalDate.of(2026, 7, 12), NOW))
                .willReturn(new ConsultingJobRegistration(13L, true));
        given(consultingRepository.findByIdAndStoreId(13L, STORE_ID))
                .willReturn(Optional.of(pending));

        ConsultingCreationResult result = consultingService.generate(
                jwt,
                STORE_ID,
                new ConsultingCreateRequest(LocalDate.of(2026, 7, 12))
        );

        assertThat(result.created()).isTrue();
        assertThat(result.consulting().consultingId()).isEqualTo(13L);
        verify(eventPublisher).publishEvent(new ConsultingGenerationRequestedEvent(13L));
    }

    @Test
    void listsAndReadsOnlyAfterValidatingStoreOwnership() {
        Consulting consulting = completedConsulting(10L);
        given(consultingRepository.findAllByStoreIdOrderByTargetDateDescIdDesc(STORE_ID))
                .willReturn(List.of(consulting));
        given(consultingRepository.findByIdAndStoreId(10L, STORE_ID))
                .willReturn(Optional.of(consulting));

        var list = consultingService.getConsultings(jwt, STORE_ID);
        var detail = consultingService.getConsulting(jwt, STORE_ID, 10L);

        assertThat(list).singleElement().satisfies(item -> {
            assertThat(item.consultingId()).isEqualTo(10L);
            assertThat(item.title()).isEqualTo("일일 분석");
        });
        assertThat(detail.content()).isEqualTo("피크 시간대 인력을 보강하세요.");
        verify(storeAccessValidator, org.mockito.Mockito.times(2)).validate(jwt, STORE_ID);
    }

    @Test
    void missingStoreScopedConsultingUsesNotFoundError() {
        given(consultingRepository.findByIdAndStoreId(999L, STORE_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> consultingService.getConsulting(jwt, STORE_ID, 999L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONSULTING_NOT_FOUND));
    }

    private ConsultingService serviceAt(LocalDateTime localDateTime) {
        return new ConsultingService(
                consultingRepository,
                storeAccessValidator,
                jobManager,
                eventPublisher,
                fixedClock(localDateTime)
        );
    }

    private Store store(LocalDateTime createdAt) {
        Store store = new Store(
                new User("owner", "hash"),
                "강남점"
        );
        TestFieldSetter.setField(store, "id", STORE_ID);
        TestFieldSetter.setField(store, "createdAt", createdAt);
        return store;
    }

    private Consulting pendingConsulting(Long id) {
        Consulting consulting = Consulting.pending(STORE_ID, TARGET_DATE, NOW);
        TestFieldSetter.setField(consulting, "id", id);
        return consulting;
    }

    private Consulting completedConsulting(Long id) {
        Consulting consulting = pendingConsulting(id);
        consulting.claim(NOW, java.time.Duration.ofMinutes(2), 3);
        consulting.complete(
                new ConsultingGenerationResult(
                        "일일 분석",
                        "피크 시간대 인력을 보강하세요."
                ),
                NOW,
                1
        );
        return consulting;
    }

    private Clock fixedClock(LocalDateTime localDateTime) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return Clock.fixed(localDateTime.atZone(kst).toInstant(), kst);
    }
}
