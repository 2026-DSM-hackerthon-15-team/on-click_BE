package com.onclick.domain.store.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import com.onclick.domain.auth.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "stores")
public class Store {

    public static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
    public static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(22, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Store() {
    }

    public Store(User owner, String name, String timeZone) {
        this(owner, name, timeZone, DEFAULT_CLOSING_TIME);
    }

    public Store(User owner, String name, String timeZone, LocalTime closingTime) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner must not be null");
        this.name = name;
        this.timeZone = timeZone;
        this.closingTime = java.util.Objects.requireNonNull(closingTime, "closingTime must not be null");
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String name, String timeZone) {
        update(name, timeZone, null);
    }

    public void update(String name, String timeZone, LocalTime closingTime) {
        if (name != null) {
            this.name = name;
        }
        if (timeZone != null) {
            this.timeZone = timeZone;
        }
        if (closingTime != null) {
            this.closingTime = closingTime;
        }
        this.updatedAt = Instant.now();
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Long getOwnerUserId() {
        return owner.getId();
    }

    public String getName() {
        return name;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
