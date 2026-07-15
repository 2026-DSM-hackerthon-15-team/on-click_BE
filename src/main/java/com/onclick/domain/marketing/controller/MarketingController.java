package com.onclick.domain.marketing.controller;

import java.util.List;

import com.onclick.domain.marketing.dto.MarketingGenerateRequest;
import com.onclick.domain.marketing.dto.MarketingResponse;
import com.onclick.domain.marketing.dto.MarketingUpdateRequest;
import com.onclick.domain.marketing.service.MarketingService;
import com.onclick.domain.marketing.service.MarketingPublishingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/stores/{storeId}/marketings")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;
    private final MarketingPublishingService marketingPublishingService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MarketingResponse generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody MarketingGenerateRequest request
    ) {
        return marketingService.generate(jwt, storeId, request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MarketingResponse generateFromProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @RequestParam Long productId,
            @RequestParam("image") MultipartFile image
    ) {
        return marketingService.generate(jwt, storeId, productId, image);
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
    public MarketingResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long marketingId
    ) {
        return marketingPublishingService.approveAndPublish(jwt, storeId, marketingId);
    }
}
