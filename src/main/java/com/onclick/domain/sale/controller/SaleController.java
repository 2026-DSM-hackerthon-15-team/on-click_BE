package com.onclick.domain.sale.controller;

import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.service.SaleService;
import com.onclick.domain.sale.service.SaleTransactionResult;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/sales/transactions")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    public ResponseEntity<SaleTransactionResponse> createTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody SaleTransactionCreateRequest request
    ) {
        SaleTransactionResult result = saleService.createTransaction(jwt, storeId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.transaction());
    }

    @PostMapping("/{saleId}/cancel")
    public SaleTransactionResponse cancelTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long saleId
    ) {
        return saleService.cancelTransaction(jwt, storeId, saleId);
    }
}
