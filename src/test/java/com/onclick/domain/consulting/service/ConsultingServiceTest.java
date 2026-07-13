package com.onclick.domain.consulting.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultingServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-07-13T13:00:00Z");

    @Mock
    private ConsultingRepository consultingRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private Jwt jwt;

    private ConsultingService consultingService;

    @BeforeEach
    void setUp() {
        consultingService = new ConsultingService(consultingRepository, storeAccessValidator);
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

    private Consulting completedConsulting(Long id) {
        Consulting consulting = Consulting.pending(STORE_ID, LocalDate.of(2026, 7, 13), NOW);
        ReflectionTestUtils.setField(consulting, "id", id);
        consulting.claim(NOW, java.time.Duration.ofMinutes(2), 3);
        consulting.complete(
                new ConsultingGenerationResult(
                        "일일 분석",
                        "피크 시간대 인력을 보강하세요.",
                        NOW
                ),
                NOW,
                1
        );
        return consulting;
    }
}
