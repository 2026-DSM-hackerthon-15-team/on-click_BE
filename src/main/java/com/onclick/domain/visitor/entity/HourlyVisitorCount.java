package com.onclick.domain.visitor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "hourly_visitor_counts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hourly_visitor_counts_store_date_hour",
                columnNames = {"store_id", "business_date", "business_hour"}
        )
)
public class HourlyVisitorCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "business_hour", nullable = false)
    private int hour;

    @Column(name = "visitor_count", nullable = false)
    private long visitorCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HourlyVisitorCount() {
    }

    public HourlyVisitorCount(
            Long storeId,
            LocalDate businessDate,
            int hour,
            long visitorCount,
            Instant now
    ) {
        this.storeId = requirePositiveStoreId(storeId);
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate must not be null");
        this.hour = requireValidHour(hour);
        this.visitorCount = requireNonNegativeVisitorCount(visitorCount);
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void updateVisitorCount(long visitorCount, Instant now) {
        this.visitorCount = requireNonNegativeVisitorCount(visitorCount);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static Long requirePositiveStoreId(Long storeId) {
        if (storeId == null || storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        return storeId;
    }

    private static int requireValidHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        return hour;
    }

    private static long requireNonNegativeVisitorCount(long visitorCount) {
        if (visitorCount < 0) {
            throw new IllegalArgumentException("visitorCount must be non-negative");
        }
        return visitorCount;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public int getHour() {
        return hour;
    }

    public long getVisitorCount() {
        return visitorCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
