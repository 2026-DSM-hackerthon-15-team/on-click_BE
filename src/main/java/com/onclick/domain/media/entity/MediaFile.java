package com.onclick.domain.media.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_files")
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "storage_name", nullable = false, unique = true, length = 100)
    private String storageName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaFile() {
    }

    public MediaFile(Long storeId, String originalName, String storageName, String contentType, long sizeBytes) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.publicId = UUID.randomUUID().toString();
        this.originalName = Objects.requireNonNull(originalName, "originalName must not be null");
        this.storageName = Objects.requireNonNull(storageName, "storageName must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageName() {
        return storageName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
