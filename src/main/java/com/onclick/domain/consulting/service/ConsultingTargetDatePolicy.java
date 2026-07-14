package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.onclick.common.time.KoreanTime;
import com.onclick.domain.store.entity.Store;

final class ConsultingTargetDatePolicy {

    private ConsultingTargetDatePolicy() {
    }

    static LocalDateTime now(Clock clock) {
        return KoreanTime.now(clock);
    }

    static LocalDate latestDueDate(Store store, LocalDateTime now) {
        return now.toLocalTime().isBefore(store.getClosingTime())
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
    }

    static LocalDate storeCreationDate(Store store) {
        return store.getCreatedAt() == null
                ? null
                : store.getCreatedAt().toLocalDate();
    }
}
