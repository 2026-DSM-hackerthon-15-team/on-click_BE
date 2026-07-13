package com.onclick.domain.consulting.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    private Instant nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Consulting() {
    }

    private Consulting(Long storeId, LocalDate targetDate, Instant now) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate must not be null");
        this.status = ConsultingStatus.PENDING;
        this.attemptCount = 0;
        this.nextRetryAt = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Consulting pending(Long storeId, LocalDate targetDate, Instant now) {
        return new Consulting(storeId, targetDate, now);
    }

    public boolean claim(Instant now, Duration leaseDuration, int maxAttempts) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (status == ConsultingStatus.COMPLETED
                || attemptCount >= maxAttempts
                || (nextRetryAt != null && nextRetryAt.isAfter(now))) {
            return false;
        }
        status = ConsultingStatus.PENDING;
        attemptCount++;
        nextRetryAt = now.plus(leaseDuration);
        lastError = null;
        updatedAt = now;
        return true;
    }

    public boolean complete(ConsultingGenerationResult result, Instant now, int expectedAttempt) {
        if (status != ConsultingStatus.PENDING || attemptCount != expectedAttempt) {
            return false;
        }
        Objects.requireNonNull(result, "result must not be null");
        this.title = requireText(result.title(), "title");
        this.content = requireText(result.content(), "content");
        this.generatedAt = Objects.requireNonNull(result.generatedAt(), "generatedAt must not be null");
        this.status = ConsultingStatus.COMPLETED;
        this.nextRetryAt = null;
        this.lastError = null;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    public boolean fail(
            String reason,
            Instant now,
            Instant retryAt,
            int maxAttempts,
            int expectedAttempt
    ) {
        if (status != ConsultingStatus.PENDING || attemptCount != expectedAttempt) {
            return false;
        }
        this.status = ConsultingStatus.FAILED;
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

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public ConsultingStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
