package com.onclick.domain.instagram.controller;

import com.onclick.domain.instagram.dto.InstagramAccountResponse;
import com.onclick.domain.instagram.dto.InstagramAccountUpsertRequest;
import com.onclick.domain.instagram.dto.InstagramCredentialsResponse;
import com.onclick.domain.instagram.service.InstagramAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/instagram-account")
@RequiredArgsConstructor
public class InstagramAccountController {

    private final InstagramAccountService service;

    @PutMapping
    public InstagramAccountResponse upsert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody InstagramAccountUpsertRequest request
    ) {
        return service.upsert(jwt, storeId, request);
    }

    @GetMapping
    public ResponseEntity<InstagramCredentialsResponse> getCredentials(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getCredentials(jwt, storeId));
    }
}
