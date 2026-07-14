package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ConsultingJobProcessor {

    private final ConsultingJobManager jobManager;
    private final ConsultingRequestFactory requestFactory;
    private final AiClient aiClient;
    private final ConsultingSchedulerProperties properties;
    private final Clock clock;

    void process(Long consultingId) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        LocalDateTime now = ConsultingTargetDatePolicy.now(clock);
        jobManager.claim(
                        consultingId,
                        now,
                        properties.getLeaseDuration(),
                        maxAttempts
                )
                .ifPresent(job -> generate(job, maxAttempts));
    }

    private void generate(ConsultingJobClaim job, int maxAttempts) {
        try {
            ConsultingGenerationRequest request = requestFactory.create(job);
            ConsultingGenerationResult result = aiClient.generateConsulting(request);
            jobManager.complete(job, result, ConsultingTargetDatePolicy.now(clock));
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
                        ConsultingTargetDatePolicy.now(clock),
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
