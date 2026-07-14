package com.onclick.domain.marketing.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.onclick.domain.media.entity.MediaFile;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "marketing_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Getter(AccessLevel.NONE)
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
    @Getter(AccessLevel.NONE)
    private List<MediaFile> mediaFiles = new ArrayList<>();

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "next_publish_at")
    private LocalDateTime nextPublishAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "external_post_id", length = 100)
    private String externalPostId;

    @Column(name = "published_url", length = 1000)
    private String publishedUrl;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;

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

    public void approve(LocalDateTime now) {
        if (status != MarketingStatus.DRAFT && status != MarketingStatus.FAILED) {
            throw new IllegalStateException("Marketing content cannot be approved from status " + status);
        }
        if (mediaFiles.isEmpty()) {
            throw new IllegalStateException("At least one media file is required");
        }
        if (status == MarketingStatus.FAILED) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        status = MarketingStatus.APPROVED;
        approvedAt = approvedAt == null ? now : approvedAt;
        nextPublishAt = null;
        failureReason = null;
    }

    public void beginPublishing(LocalDateTime now) {
        if (status != MarketingStatus.APPROVED) {
            throw new IllegalStateException("Marketing content is not ready to publish");
        }
        status = MarketingStatus.PUBLISHING;
        publishAttemptCount++;
        nextPublishAt = null;
        failureReason = null;
    }

    public void markPublished(String externalPostId, String publishedUrl, LocalDateTime publishedAt) {
        if (status != MarketingStatus.PUBLISHING) {
            throw new IllegalStateException("Marketing content is not being published");
        }
        status = MarketingStatus.PUBLISHED;
        this.externalPostId = externalPostId;
        this.publishedUrl = publishedUrl;
        this.publishedAt = publishedAt;
        this.nextPublishAt = null;
        this.failureReason = null;
    }

    public void markPublishFailed(String reason, LocalDateTime retryAt, boolean retryable) {
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

    public List<MediaFile> getMediaFiles() { return List.copyOf(mediaFiles); }
}
