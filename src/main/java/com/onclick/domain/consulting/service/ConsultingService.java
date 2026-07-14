package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.onclick.domain.consulting.dto.ConsultingCreateRequest;
import com.onclick.domain.consulting.dto.ConsultingDetailResponse;
import com.onclick.domain.consulting.dto.ConsultingSummaryResponse;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConsultingService {

    private final ConsultingRepository consultingRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final ConsultingJobManager jobManager;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ConsultingCreationResult generate(
            Jwt jwt,
            Long storeId,
            ConsultingCreateRequest request
    ) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        LocalDate targetDate = request == null ? null : request.targetDate();
        LocalDateTime now = ConsultingTargetDatePolicy.now(clock);
        validateTargetDate(store, targetDate, now);

        ConsultingJobRegistration registration;
        try {
            registration = jobManager.createPending(storeId, targetDate, now);
        } catch (DataIntegrityViolationException exception) {
            // Another API request or scheduler may have inserted the same unique business date.
            Consulting raced = findByTargetDate(storeId, targetDate);
            registration = new ConsultingJobRegistration(raced.getId(), false);
        }

        Consulting consulting = consultingRepository
                .findByIdAndStoreId(registration.consultingId(), storeId)
                .orElseThrow(() -> new IllegalStateException("Registered consulting could not be found"));
        if (registration.created()) {
            eventPublisher.publishEvent(
                    new ConsultingGenerationRequestedEvent(registration.consultingId())
            );
        }
        return new ConsultingCreationResult(
                ConsultingDetailResponse.from(consulting),
                registration.created()
        );
    }

    public List<ConsultingSummaryResponse> getConsultings(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        return consultingRepository.findAllByStoreIdOrderByTargetDateDescIdDesc(storeId)
                .stream()
                .map(ConsultingSummaryResponse::from)
                .toList();
    }

    public ConsultingDetailResponse getConsulting(Jwt jwt, Long storeId, Long consultingId) {
        storeAccessValidator.validate(jwt, storeId);
        Consulting consulting = consultingRepository.findByIdAndStoreId(consultingId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONSULTING_NOT_FOUND));
        return ConsultingDetailResponse.from(consulting);
    }

    private void validateTargetDate(Store store, LocalDate targetDate, LocalDateTime now) {
        if (targetDate == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "컨설팅 대상 영업일을 입력해 주세요.");
        }
        LocalDate storeCreationDate = ConsultingTargetDatePolicy.storeCreationDate(store);
        if (storeCreationDate != null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "매장 생성일 이전 영업일의 컨설팅은 생성할 수 없습니다."
            );
        }
    }

    private Consulting findByTargetDate(Long storeId, LocalDate targetDate) {
        return consultingRepository.findByStoreIdAndTargetDate(storeId, targetDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Concurrent consulting registration could not be resolved"
                ));
    }
}
