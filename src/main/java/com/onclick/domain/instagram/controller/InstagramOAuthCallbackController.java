package com.onclick.domain.instagram.controller;

import java.net.URI;

import com.onclick.domain.instagram.service.InstagramIntegrationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/instagram")
public class InstagramOAuthCallbackController {

    private final InstagramIntegrationService service;

    public InstagramOAuthCallbackController(InstagramIntegrationService service) {
        this.service = service;
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        Long storeId = service.completeCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(service.frontendRedirectUrl(true, storeId)))
                .build();
    }
}
