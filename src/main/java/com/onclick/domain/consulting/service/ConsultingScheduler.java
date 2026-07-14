package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.consulting",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class ConsultingScheduler {

    private final StoreRepository storeRepository;
    private final ConsultingJobManager jobManager;
    private final ConsultingJobProcessor jobProcessor;
    private final ConsultingSchedulerProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.consulting.scheduler-delay:PT1M}",
            initialDelayString = "${app.consulting.initial-delay:PT10S}"
    )
    public void generateDueConsultings() {
        LocalDateTime now = ConsultingTargetDatePolicy.now(clock);
        for (Store store : storeRepository.findAll()) {
            createLatestDueConsulting(store, now);
        }
        processRetryableConsultings(now);
    }

    private void createLatestDueConsulting(Store store, LocalDateTime now) {
        LocalDate targetDate = ConsultingTargetDatePolicy.latestDueDate(store, now);
        if (store.getCreatedAt() != null
                && targetDate.isBefore(store.getCreatedAt().toLocalDate())) {
            return;
        }
        try {
            jobManager.createPending(store.getId(), targetDate, now);
        } catch (DataIntegrityViolationException exception) {
            log.debug(
                    "A consulting already exists for store {} and business date {}",
                    store.getId(),
                    targetDate
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to create a pending consulting for store {} and business date {}",
                    store.getId(),
                    targetDate,
                    exception
            );
        }
    }

    private void processRetryableConsultings(LocalDateTime now) {
        List<Long> retryableIds = jobManager.findRetryableIds(
                now,
                properties.safeBatchSize()
        );
        for (Long consultingId : retryableIds) {
            jobProcessor.process(consultingId);
        }
    }
}
