package com.onclick.domain.marketing.dto;

import java.time.Instant;
import java.util.List;

import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.entity.MarketingStatus;
import com.onclick.domain.media.service.MediaStorageService;

public record MarketingResponse(
        Long marketingId,
        Long storeId,
        String title,
        String content,
        List<String> hashtags,
        List<MarketingMediaResponse> media,
        MarketingStatus status,
        int publishAttemptCount,
        String externalPostId,
        String publishedUrl,
        String failureReason,
        Instant approvedAt,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static MarketingResponse from(MarketingContent marketing, MediaStorageService storage) {
        return new MarketingResponse(
                marketing.getId(),
                marketing.getStoreId(),
                marketing.getTitle(),
                marketing.getContent(),
                marketing.hashtagList(),
                marketing.getMediaFiles().stream()
                        .map(mediaFile -> new MarketingMediaResponse(
                                mediaFile.getId(),
                                mediaFile.getOriginalName(),
                                storage.publicUrl(mediaFile)
                        ))
                        .toList(),
                marketing.getStatus(),
                marketing.getPublishAttemptCount(),
                marketing.getExternalPostId(),
                marketing.getPublishedUrl(),
                marketing.getFailureReason(),
                marketing.getApprovedAt(),
                marketing.getPublishedAt(),
                marketing.getCreatedAt(),
                marketing.getUpdatedAt()
        );
    }

    public record MarketingMediaResponse(Long mediaId, String originalName, String publicUrl) {
    }
}
