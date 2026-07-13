package com.onclick.domain.store.controller;

import java.util.List;

import com.onclick.domain.store.dto.CreateStoreRequest;
import com.onclick.domain.store.dto.StoreResponse;
import com.onclick.domain.store.dto.UpdateStoreRequest;
import com.onclick.domain.store.service.StoreService;

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
@RequestMapping("/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public List<StoreResponse> getStores(@AuthenticationPrincipal Jwt jwt) {
        return storeService.getStores(jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse createStore(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateStoreRequest request
    ) {
        return storeService.createStore(jwt, request);
    }

    @PatchMapping("/{storeId}")
    public StoreResponse updateStore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody UpdateStoreRequest request
    ) {
        return storeService.updateStore(jwt, storeId, request);
    }
}
