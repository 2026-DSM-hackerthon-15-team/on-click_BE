package com.onclick.domain.instagram.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.onclick.domain.store.entity.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "instagram_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Getter(AccessLevel.NONE)
    @Column(name = "password_plaintext", nullable = false, length = 255)
    private String password;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public InstagramAccount(Store store, String accountId, String password) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        updateCredentials(accountId, password);
    }

    public void updateCredentials(String accountId, String password) {
        this.accountId = requireAccountId(accountId);
        this.password = requirePassword(password);
    }

    public String revealPassword() {
        return password;
    }

    public Long getStoreId() {
        return store.getId();
    }

    private String requireAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("accountId must be between 1 and 100 characters");
        }
        return normalized;
    }

    private String requirePassword(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("password must be between 1 and 255 characters");
        }
        return value;
    }

}
