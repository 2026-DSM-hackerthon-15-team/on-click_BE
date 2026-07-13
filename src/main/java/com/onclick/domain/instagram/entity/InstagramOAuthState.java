package com.onclick.domain.instagram.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "instagram_oauth_states")
public class InstagramOAuthState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InstagramOAuthState() {
    }

    public InstagramOAuthState(String stateHash, Long storeId, Long userId, Instant expiresAt) {
        this.stateHash = Objects.requireNonNull(stateHash, "stateHash must not be null");
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public void consume(Instant now) {
        if (usedAt != null || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("OAuth state is expired or already used");
        }
        usedAt = now;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getStateHash() { return stateHash; }
    public Long getStoreId() { return storeId; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
