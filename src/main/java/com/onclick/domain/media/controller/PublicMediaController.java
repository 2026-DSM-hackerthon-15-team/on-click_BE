package com.onclick.domain.media.controller;

import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.domain.media.service.MediaStorageService.StoredMedia;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/public/media")
@RequiredArgsConstructor
public class PublicMediaController {

    private final MediaStorageService mediaStorageService;

    @GetMapping("/{publicId}")
    public ResponseEntity<?> get(@PathVariable String publicId) {
        StoredMedia media = mediaStorageService.loadPublic(publicId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(media.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(media.resource());
    }
}
