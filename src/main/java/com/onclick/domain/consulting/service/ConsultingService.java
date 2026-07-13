package com.onclick.domain.consulting.service;

import java.util.List;

import com.onclick.domain.consulting.dto.ConsultingDetailResponse;
import com.onclick.domain.consulting.dto.ConsultingSummaryResponse;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultingService {

    private final ConsultingRepository consultingRepository;
    private final StoreAccessValidator storeAccessValidator;

    public ConsultingService(
            ConsultingRepository consultingRepository,
            StoreAccessValidator storeAccessValidator
    ) {
        this.consultingRepository = consultingRepository;
        this.storeAccessValidator = storeAccessValidator;
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
}
