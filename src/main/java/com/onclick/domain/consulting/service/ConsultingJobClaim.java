package com.onclick.domain.consulting.service;

import java.time.LocalDate;

public record ConsultingJobClaim(
        Long consultingId,
        Long storeId,
        LocalDate targetDate,
        int attempt
) {
}
