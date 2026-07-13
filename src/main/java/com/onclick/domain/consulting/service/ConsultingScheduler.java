package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ConsultingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsultingScheduler.class);

    private final StoreRepository storeRepository;
    private final ConsultingJobManager jobManager;
    private final ConsultingRequestFactory requestFactory;
    private final AiClient aiClient;
    private final ConsultingSchedulerProperties properties;
    private final Clock clock;

    public ConsultingScheduler(
            StoreRepository storeRepository,
            ConsultingJobManager jobManager,
            ConsultingRequestFactory requestFactory,
            AiClient aiClient,
            ConsultingSchedulerProperties properties,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.jobManager = jobManager;
        this.requestFactory = requestFactory;
        this.aiClient = aiClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.consulting.scheduler-delay:PT1M}",
            initialDelayString = "${app.consulting.initial-delay:PT10S}"
    )
    public void generateDueConsultings() {
        Instant now = clock.instant();
        for (Store store : storeRepository.findAll()) {
            createLatestDueConsulting(store, now);
        }
        processRetryableConsultings(now);
    }

    private void createLatestDueConsulting(Store store, Instant now) {
        ZonedDateTime localNow = now.atZone(store.zoneId());
        LocalDate targetDate = localNow.toLocalTime().isBefore(store.getClosingTime())
                ? localNow.toLocalDate().minusDays(1)
                : localNow.toLocalDate();
        if (store.getCreatedAt() != null
                && targetDate.isBefore(store.getCreatedAt().atZone(store.zoneId()).toLocalDate())) {
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

    private void processRetryableConsultings(Instant now) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<Long> retryableIds = jobManager.findRetryableIds(
                now,
                maxAttempts,
                properties.getBatchSize()
        );
        for (Long consultingId : retryableIds) {
            jobManager.claim(
                            consultingId,
                            clock.instant(),
                            properties.getLeaseDuration(),
                            maxAttempts
                    )
                    .ifPresent(job -> generate(job, maxAttempts));
        }
    }

    private void generate(ConsultingJobClaim job, int maxAttempts) {
        try {
            ConsultingGenerationRequest request = requestFactory.create(job);
            ConsultingGenerationResult result = aiClient.generateConsulting(request);
            jobManager.complete(job, result, clock.instant());
        } catch (RuntimeException exception) {
            log.warn(
                    "Consulting generation failed for consulting {} (store {}, business date {})",
                    job.consultingId(),
                    job.storeId(),
                    job.targetDate(),
                    exception
            );
            try {
                jobManager.fail(
                        job,
                        exception.getMessage(),
                        clock.instant(),
                        properties.getRetryDelay(),
                        maxAttempts
                );
            } catch (RuntimeException persistenceFailure) {
                log.error(
                        "Failed to persist consulting generation failure for consulting {}",
                        job.consultingId(),
                        persistenceFailure
                );
            }
        }
    }
}
