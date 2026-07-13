package com.onclick.domain.marketing.controller;

import java.util.List;

import com.onclick.domain.marketing.dto.MarketingGenerateRequest;
import com.onclick.domain.marketing.dto.MarketingResponse;
import com.onclick.domain.marketing.dto.MarketingUpdateRequest;
import com.onclick.domain.marketing.service.MarketingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/marketings")
public class MarketingController {

    private final MarketingService marketingService;

    public MarketingController(MarketingService marketingService) {
        this.marketingService = marketingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketingResponse generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody MarketingGenerateRequest request
    ) {
        return marketingService.generate(jwt, storeId, request);
    }

    @GetMapping
    public List<MarketingResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return marketingService.findAll(jwt, storeId);
    }

    @GetMapping("/{marketingId}")
    public MarketingResponse findOne(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long marketingId
    ) {
        return marketingService.findOne(jwt, storeId, marketingId);
    }

    @PatchMapping("/{marketingId}")
    public MarketingResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long marketingId,
            @Valid @RequestBody MarketingUpdateRequest request
    ) {
        return marketingService.update(jwt, storeId, marketingId, request);
    }

    @PostMapping("/{marketingId}/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MarketingResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long marketingId
    ) {
        return marketingService.approve(jwt, storeId, marketingId);
    }
}
