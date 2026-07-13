package com.onclick.domain.instagram.controller;

import java.net.URI;

import com.onclick.domain.instagram.dto.InstagramConnectResponse;
import com.onclick.domain.instagram.dto.InstagramStatusResponse;
import com.onclick.domain.instagram.service.InstagramIntegrationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/integrations/instagram")
public class InstagramIntegrationController {

    private final InstagramIntegrationService service;

    public InstagramIntegrationController(InstagramIntegrationService service) {
        this.service = service;
    }

    @PostMapping("/connect")
    public InstagramConnectResponse connect(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return service.connect(jwt, storeId);
    }

    @GetMapping
    public InstagramStatusResponse status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return service.status(jwt, storeId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        service.disconnect(jwt, storeId);
    }
}
