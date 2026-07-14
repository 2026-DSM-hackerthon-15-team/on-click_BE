package com.onclick.domain.sale.controller;

import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionPageResponse;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.service.SaleService;
import com.onclick.domain.sale.service.SaleTransactionResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/sales/transactions")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public SaleTransactionPageResponse findTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "soldAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return saleService.findTransactions(
                jwt,
                storeId,
                page,
                size,
                sortBy,
                sortDirection
        );
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
