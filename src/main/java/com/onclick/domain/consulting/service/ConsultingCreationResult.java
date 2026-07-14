package com.onclick.domain.consulting.service;

import com.onclick.domain.consulting.dto.ConsultingDetailResponse;

public record ConsultingCreationResult(
        ConsultingDetailResponse consulting,
        boolean created
) {
}
