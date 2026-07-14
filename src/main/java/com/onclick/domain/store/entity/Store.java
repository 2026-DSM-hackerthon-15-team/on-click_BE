package com.onclick.domain.store.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.onclick.common.time.KoreanTime;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.instagram.entity.InstagramAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    public static final Industry DEFAULT_INDUSTRY = Industry.OTHER;
    public static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(22, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @OneToOne(mappedBy = "store", fetch = FetchType.LAZY)
    private InstagramAccount instagramAccount;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Industry industry;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Store(User owner, String name) {
        this(owner, name, DEFAULT_INDUSTRY, null, DEFAULT_CLOSING_TIME);
    }

    public Store(User owner, String name, LocalTime closingTime) {
        this(owner, name, DEFAULT_INDUSTRY, null, closingTime);
    }

    public Store(
            User owner,
            String name,
            Industry industry,
            String roadAddress,
            LocalTime closingTime
    ) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner must not be null");
        this.name = name;
        this.industry = java.util.Objects.requireNonNull(industry, "industry must not be null");
        this.roadAddress = roadAddress;
        this.closingTime = java.util.Objects.requireNonNull(closingTime, "closingTime must not be null");
    }

    public void update(String name) {
        update(name, null, null, null);
    }

    public void update(String name, LocalTime closingTime) {
        update(name, null, null, closingTime);
    }

    public void update(
            String name,
            Industry industry,
            String roadAddress,
            LocalTime closingTime
    ) {
        if (name != null) {
            this.name = name;
        }
        if (industry != null) {
            this.industry = industry;
        }
        if (roadAddress != null) {
            this.roadAddress = roadAddress;
        }
        if (closingTime != null) {
            this.closingTime = closingTime;
        }
        this.updatedAt = KoreanTime.now();
    }

    public Long getOwnerUserId() {
        return owner.getId();
    }
}
