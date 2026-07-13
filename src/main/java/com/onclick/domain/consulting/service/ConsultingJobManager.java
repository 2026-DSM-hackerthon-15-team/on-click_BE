package com.onclick.domain.consulting.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultingJobManager {

    private final ConsultingRepository consultingRepository;

    public ConsultingJobManager(ConsultingRepository consultingRepository) {
        this.consultingRepository = consultingRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createPending(Long storeId, LocalDate targetDate, Instant now) {
        if (consultingRepository.existsByStoreIdAndTargetDate(storeId, targetDate)) {
            return;
        }
        consultingRepository.saveAndFlush(Consulting.pending(storeId, targetDate, now));
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryableIds(Instant now, int maxAttempts, int batchSize) {
        return consultingRepository.findRetryableIds(
                now,
                maxAttempts,
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ConsultingJobClaim> claim(
            Long consultingId,
            Instant now,
            Duration leaseDuration,
            int maxAttempts
    ) {
        Consulting consulting = consultingRepository.findByIdForUpdate(consultingId).orElse(null);
        if (consulting == null || !consulting.claim(now, leaseDuration, maxAttempts)) {
            return Optional.empty();
        }
        return Optional.of(new ConsultingJobClaim(
                consulting.getId(),
                consulting.getStoreId(),
                consulting.getTargetDate(),
                consulting.getAttemptCount()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(ConsultingJobClaim job, ConsultingGenerationResult result, Instant now) {
        consultingRepository.findByIdForUpdate(job.consultingId())
                .ifPresent(consulting -> consulting.complete(result, now, job.attempt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            ConsultingJobClaim job,
            String reason,
            Instant now,
            Duration retryDelay,
            int maxAttempts
    ) {
        consultingRepository.findByIdForUpdate(job.consultingId())
                .ifPresent(consulting -> consulting.fail(
                        reason,
                        now,
                        now.plus(retryDelay),
                        maxAttempts,
                        job.attempt()
                ));
    }
}
