package com.onclick.domain.instagram.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "instagram_integrations")
public class InstagramIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, unique = true)
    private Long storeId;

    @Column(name = "instagram_user_id", nullable = false, length = 100)
    private String instagramUserId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InstagramIntegration() {
    }

    public InstagramIntegration(
            Long storeId,
            String instagramUserId,
            String username,
            String accessTokenEncrypted,
            Instant tokenExpiresAt,
            Instant connectedAt
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        updateCredentials(instagramUserId, username, accessTokenEncrypted, tokenExpiresAt, connectedAt);
    }

    public void updateCredentials(
            String instagramUserId,
            String username,
            String accessTokenEncrypted,
            Instant tokenExpiresAt,
            Instant connectedAt
    ) {
        this.instagramUserId = requireText(instagramUserId, "instagramUserId");
        this.username = requireText(username, "username");
        this.accessTokenEncrypted = requireText(accessTokenEncrypted, "accessTokenEncrypted");
        this.tokenExpiresAt = Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt must not be null");
        this.connectedAt = Objects.requireNonNull(connectedAt, "connectedAt must not be null");
    }

    public void refreshToken(String accessTokenEncrypted, Instant tokenExpiresAt) {
        this.accessTokenEncrypted = requireText(accessTokenEncrypted, "accessTokenEncrypted");
        this.tokenExpiresAt = Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt must not be null");
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

    private String requireText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getInstagramUserId() { return instagramUserId; }
    public String getUsername() { return username; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
