package com.onclick.domain.consulting.service;

import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConsultingRequestFactory {

    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public ConsultingGenerationRequest create(ConsultingJobClaim job) {
        Store store = storeRepository.findById(job.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        return new ConsultingGenerationRequest(
                store.getOwnerUserId(),
                store.getId(),
                job.targetDate(),
                ConsultingGenerationRequest.DAILY_V1
        );
    }
}
