package com.onclick.common.ai.dto;

import java.time.LocalDateTime;

public record InstagramPublishResult(
        Long marketingId,
        String platform,
        InstagramPublishStatus status,
        String externalPostId,
        String publishedUrl,
        LocalDateTime publishedAt,
        String failureReason
) {
}
