package com.onclick.domain.media.dto;

import java.time.LocalDateTime;

import com.onclick.domain.media.entity.MediaFile;

public record MediaUploadResponse(
        Long mediaId,
        String originalName,
        String contentType,
        long sizeBytes,
        String publicUrl,
        LocalDateTime createdAt
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
