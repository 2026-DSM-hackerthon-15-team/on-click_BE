package com.onclick.domain.consulting.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.onclick.common.ai.dto.ConsultingGenerationResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "consultings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_consultings_store_target_date",
                columnNames = {"store_id", "target_date"}
        ),
        indexes = {
                @Index(
                        name = "idx_consultings_store_created",
                        columnList = "store_id,created_at"
                ),
                @Index(
                        name = "idx_consultings_retry",
                        columnList = "status,next_retry_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consulting {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultingStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Consulting(Long storeId, LocalDate targetDate, LocalDateTime now) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate must not be null");
        this.status = ConsultingStatus.PENDING;
        this.attemptCount = 0;
        this.nextRetryAt = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Consulting pending(Long storeId, LocalDate targetDate, LocalDateTime now) {
        return new Consulting(storeId, targetDate, now);
    }

    public boolean restartIfFailed(LocalDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != ConsultingStatus.FAILED) {
            return false;
        }
        status = ConsultingStatus.PENDING;
        attemptCount = 0;
        nextRetryAt = now;
        lastError = null;
        title = null;
        content = null;
        generatedAt = null;
        updatedAt = now;
        return true;
    }

    public boolean claim(LocalDateTime now, Duration leaseDuration, int maxAttempts) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (status != ConsultingStatus.PENDING
                || (nextRetryAt != null && nextRetryAt.isAfter(now))) {
            return false;
        }
        if (attemptCount >= maxAttempts) {
            status = ConsultingStatus.FAILED;
            nextRetryAt = null;
            lastError = truncate("AI 컨설팅 생성 작업이 완료되지 않은 채 제한 횟수에 도달했습니다.");
            updatedAt = now;
            return false;
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        status = ConsultingStatus.PENDING;
        attemptCount++;
        nextRetryAt = now.plus(leaseDuration);
        lastError = null;
        updatedAt = now;
        return true;
    }

    public boolean complete(ConsultingGenerationResult result, LocalDateTime now, int expectedAttempt) {
        if (status != ConsultingStatus.PENDING || attemptCount != expectedAttempt) {
            return false;
        }
        Objects.requireNonNull(result, "result must not be null");
        this.title = requireText(result.title(), "title");
        this.content = requireText(result.content(), "content");
        this.generatedAt = Objects.requireNonNull(now, "now must not be null");
        this.status = ConsultingStatus.COMPLETED;
        this.nextRetryAt = null;
        this.lastError = null;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    public boolean fail(
            String reason,
            LocalDateTime now,
            LocalDateTime retryAt,
            int maxAttempts,
            int expectedAttempt
    ) {
        if (status != ConsultingStatus.PENDING || attemptCount != expectedAttempt) {
            return false;
        }
        this.status = attemptCount < maxAttempts
                ? ConsultingStatus.PENDING
                : ConsultingStatus.FAILED;
        this.lastError = truncate(reason);
        this.nextRetryAt = attemptCount < maxAttempts ? retryAt : null;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private String truncate(String value) {
        String message = value == null || value.isBlank()
                ? "AI 컨설팅 생성에 실패했습니다."
                : value;
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }

}
