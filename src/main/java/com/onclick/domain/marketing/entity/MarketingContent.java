package com.onclick.domain.marketing.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.onclick.domain.media.entity.MediaFile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketing_contents")
public class MarketingContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hashtags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketingStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "marketing_media",
            joinColumns = @JoinColumn(name = "marketing_id"),
            inverseJoinColumns = @JoinColumn(name = "media_id")
    )
    @OrderColumn(name = "position")
    private List<MediaFile> mediaFiles = new ArrayList<>();

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "next_publish_at")
    private Instant nextPublishAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "external_post_id", length = 100)
    private String externalPostId;

    @Column(name = "published_url", length = 1000)
    private String publishedUrl;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketingContent() {
    }

    public MarketingContent(
            Long storeId,
            String title,
            String content,
            Collection<String> hashtags,
            Collection<MediaFile> mediaFiles
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.hashtags = encodeHashtags(hashtags);
        this.mediaFiles.addAll(mediaFiles == null ? List.of() : mediaFiles);
        this.status = MarketingStatus.DRAFT;
        this.idempotencyKey = UUID.randomUUID().toString();
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

    public void edit(
            String title,
            String content,
            Collection<String> hashtags,
            Collection<MediaFile> mediaFiles
    ) {
        if (status != MarketingStatus.DRAFT) {
            throw new IllegalStateException("Only draft marketing content can be edited");
        }
        if (title != null) {
            this.title = requireText(title, "title");
        }
        if (content != null) {
            this.content = requireText(content, "content");
        }
        if (hashtags != null) {
            this.hashtags = encodeHashtags(hashtags);
        }
        if (mediaFiles != null) {
            this.mediaFiles.clear();
            this.mediaFiles.addAll(mediaFiles);
        }
    }

    public void approve(Instant now) {
        if (status != MarketingStatus.DRAFT && status != MarketingStatus.FAILED) {
            throw new IllegalStateException("Marketing content cannot be approved from status " + status);
        }
        if (mediaFiles.isEmpty()) {
            throw new IllegalStateException("At least one media file is required");
        }
        status = MarketingStatus.APPROVED;
        approvedAt = approvedAt == null ? now : approvedAt;
        nextPublishAt = now;
        failureReason = null;
    }

    public void beginPublishing(Instant now) {
        if (status != MarketingStatus.APPROVED) {
            throw new IllegalStateException("Marketing content is not ready to publish");
        }
        status = MarketingStatus.PUBLISHING;
        publishAttemptCount++;
        nextPublishAt = now.plusSeconds(300);
        failureReason = null;
    }

    public void markPublished(String externalPostId, String publishedUrl, Instant publishedAt) {
        status = MarketingStatus.PUBLISHED;
        this.externalPostId = externalPostId;
        this.publishedUrl = publishedUrl;
        this.publishedAt = publishedAt;
        this.nextPublishAt = null;
        this.failureReason = null;
    }

    public void markPublishFailed(String reason, Instant retryAt, boolean retryable) {
        failureReason = truncate(reason, 1000);
        if (retryable) {
            status = MarketingStatus.APPROVED;
            nextPublishAt = retryAt;
        } else {
            status = MarketingStatus.FAILED;
            nextPublishAt = null;
        }
    }

    public void markPublishUncertain(String reason) {
        if (status != MarketingStatus.PUBLISHING) {
            return;
        }
        status = MarketingStatus.FAILED;
        failureReason = truncate(reason, 1000);
        nextPublishAt = null;
    }

    public List<String> hashtagList() {
        return hashtags.isBlank() ? List.of() : hashtags.lines().toList();
    }

    private static String encodeHashtags(Collection<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return "";
        }
        return hashtags.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(30)
                .map(value -> value.startsWith("#") ? value : "#" + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String requireText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "Instagram 게시에 실패했습니다.";
        }
        return value.substring(0, Math.min(value.length(), maxLength));
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public MarketingStatus getStatus() { return status; }
    public List<MediaFile> getMediaFiles() { return List.copyOf(mediaFiles); }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getPublishAttemptCount() { return publishAttemptCount; }
    public Instant getNextPublishAt() { return nextPublishAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getExternalPostId() { return externalPostId; }
    public String getPublishedUrl() { return publishedUrl; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
