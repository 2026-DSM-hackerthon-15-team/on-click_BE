package com.onclick.domain.visitor.controller;

import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.domain.visitor.dto.HourlyVisitorResponse;
import com.onclick.domain.visitor.dto.HourlyVisitorUpsertRequest;
import com.onclick.domain.visitor.service.HourlyVisitorService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/visitors")
public class HourlyVisitorController {

    private final HourlyVisitorService hourlyVisitorService;
    private final StoreAccessValidator storeAccessValidator;

    public HourlyVisitorController(
            HourlyVisitorService hourlyVisitorService,
            StoreAccessValidator storeAccessValidator
    ) {
        this.hourlyVisitorService = hourlyVisitorService;
        this.storeAccessValidator = storeAccessValidator;
    }

    @PutMapping("/hourly")
    public HourlyVisitorResponse upsertHourlyVisitorCount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody HourlyVisitorUpsertRequest request
    ) {
        UserStoreMembership membership = storeAccessValidator.validate(jwt, storeId);
        return hourlyVisitorService.upsert(storeId, membership.getStore().zoneId(), request);
    }
}
