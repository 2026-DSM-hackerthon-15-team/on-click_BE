package com.onclick.domain.consulting.controller;

import java.net.URI;
import java.util.List;

import com.onclick.domain.consulting.dto.ConsultingCreateRequest;
import com.onclick.domain.consulting.dto.ConsultingDetailResponse;
import com.onclick.domain.consulting.dto.ConsultingSummaryResponse;
import com.onclick.domain.consulting.service.ConsultingCreationResult;
import com.onclick.domain.consulting.service.ConsultingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/consultings")
@RequiredArgsConstructor
public class ConsultingController {

    private final ConsultingService consultingService;

    @PostMapping
    public ResponseEntity<ConsultingDetailResponse> generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody ConsultingCreateRequest request
    ) {
        ConsultingCreationResult result = consultingService.generate(jwt, storeId, request);
        Long consultingId = result.consulting().consultingId();
        URI location = URI.create("/stores/%d/consultings/%d".formatted(storeId, consultingId));
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(location)
                .body(result.consulting());
    }

    @GetMapping
    public List<ConsultingSummaryResponse> getConsultings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return consultingService.getConsultings(jwt, storeId);
    }

    @GetMapping("/{consultingId}")
    public ConsultingDetailResponse getConsulting(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long consultingId
    ) {
        return consultingService.getConsulting(jwt, storeId, consultingId);
    }
}
