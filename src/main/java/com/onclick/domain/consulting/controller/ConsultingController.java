package com.onclick.domain.consulting.controller;

import java.util.List;

import com.onclick.domain.consulting.dto.ConsultingDetailResponse;
import com.onclick.domain.consulting.dto.ConsultingSummaryResponse;
import com.onclick.domain.consulting.service.ConsultingService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/consultings")
public class ConsultingController {

    private final ConsultingService consultingService;

    public ConsultingController(ConsultingService consultingService) {
        this.consultingService = consultingService;
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
