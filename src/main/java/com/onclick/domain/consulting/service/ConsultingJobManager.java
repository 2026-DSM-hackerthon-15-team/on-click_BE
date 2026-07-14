package com.onclick.domain.consulting.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.repository.ConsultingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultingJobManager {

    private final ConsultingRepository consultingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsultingJobRegistration createPending(
            Long storeId,
            LocalDate targetDate,
            LocalDateTime now
    ) {
        Optional<Consulting> existing = consultingRepository.findByStoreIdAndTargetDate(
                storeId,
                targetDate
        );
        if (existing.isPresent()) {
            return new ConsultingJobRegistration(existing.orElseThrow().getId(), false);
        }
        Consulting created = consultingRepository.saveAndFlush(
                Consulting.pending(storeId, targetDate, now)
        );
        return new ConsultingJobRegistration(created.getId(), true);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryableIds(LocalDateTime now, int batchSize) {
        return consultingRepository.findRetryableIds(
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ConsultingJobClaim> claim(
            Long consultingId,
            LocalDateTime now,
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
    public void complete(
            ConsultingJobClaim job,
            ConsultingGenerationResult result,
            LocalDateTime now
    ) {
        consultingRepository.findByIdForUpdate(job.consultingId())
                .ifPresent(consulting -> consulting.complete(result, now, job.attempt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            ConsultingJobClaim job,
            String reason,
            LocalDateTime now,
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
