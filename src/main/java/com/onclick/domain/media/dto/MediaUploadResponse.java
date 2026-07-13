package com.onclick.domain.media.dto;

import java.time.Instant;

import com.onclick.domain.media.entity.MediaFile;

public record MediaUploadResponse(
        Long mediaId,
        String originalName,
        String contentType,
        long sizeBytes,
        String publicUrl,
        Instant createdAt
) {
    public static MediaUploadResponse from(MediaFile mediaFile, String publicUrl) {
        return new MediaUploadResponse(
                mediaFile.getId(),
                mediaFile.getOriginalName(),
                mediaFile.getContentType(),
                mediaFile.getSizeBytes(),
                publicUrl,
                mediaFile.getCreatedAt()
        );
    }
}
