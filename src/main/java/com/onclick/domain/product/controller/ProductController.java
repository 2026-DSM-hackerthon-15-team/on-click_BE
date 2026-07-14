package com.onclick.domain.product.controller;

import java.util.List;

import com.onclick.domain.product.dto.ProductCreateRequest;
import com.onclick.domain.product.dto.ProductResponse;
import com.onclick.domain.product.dto.ProductStatusUpdateRequest;
import com.onclick.domain.product.dto.ProductUpdateRequest;
import com.onclick.domain.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
@RequestMapping("/stores/{storeId}/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        return productService.create(jwt, storeId, request);
    }

    @GetMapping
    public List<ProductResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return productService.findAll(jwt, storeId);
    }

    @PatchMapping("/{productId}")
    public ProductResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return productService.update(jwt, storeId, productId, request);
    }

    @PatchMapping("/{productId}/status")
    public ProductResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        return productService.updateStatus(jwt, storeId, productId, request);
    }
}
