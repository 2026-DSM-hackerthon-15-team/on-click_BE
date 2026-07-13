package com.onclick.domain.sale.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.entity.SaleItem;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class SaleService {

    private final SaleTransactionExecutor transactionExecutor;
    private final StoreAccessValidator storeAccessValidator;
    private final Clock clock;

    public SaleService(
            SaleTransactionExecutor transactionExecutor,
            StoreAccessValidator storeAccessValidator,
            Clock clock
    ) {
        this.transactionExecutor = transactionExecutor;
        this.storeAccessValidator = storeAccessValidator;
        this.clock = clock;
    }

    public SaleTransactionResult createTransaction(
            Jwt jwt,
            Long storeId,
            SaleTransactionCreateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        String clientTransactionId = normalizeClientTransactionId(request.clientTransactionId());
        Instant soldAt = normalizeDatabaseInstant(request.soldAt());
        validateUniqueLineNumbers(request.items());

        if (clientTransactionId != null) {
            SaleTransaction existing = transactionExecutor
                    .findByClientTransactionId(storeId, clientTransactionId)
                    .orElse(null);
            if (existing != null) {
                return existingResult(existing, soldAt, request);
            }
        }

        try {
            SaleTransaction saved = transactionExecutor.create(
                    storeId,
                    clientTransactionId,
                    soldAt,
                    request.items()
            );
            return new SaleTransactionResult(SaleTransactionResponse.from(saved), true);
        } catch (DataIntegrityViolationException exception) {
            if (clientTransactionId == null) {
                throw exception;
            }

            SaleTransaction concurrentWinner = transactionExecutor
                    .findByClientTransactionId(storeId, clientTransactionId)
                    .orElseThrow(() -> transactionConflict(exception));
            if (!hasSamePayload(concurrentWinner, soldAt, request)) {
                throw transactionConflict(exception);
            }
            return new SaleTransactionResult(
                    SaleTransactionResponse.from(concurrentWinner),
                    false
            );
        }
    }

    public SaleTransactionResponse cancelTransaction(Jwt jwt, Long storeId, Long saleId) {
        storeAccessValidator.validate(jwt, storeId);
        SaleTransaction transaction = transactionExecutor.cancel(
                storeId,
                saleId,
                normalizeDatabaseInstant(clock.instant())
        );
        return SaleTransactionResponse.from(transaction);
    }

    private SaleTransactionResult existingResult(
            SaleTransaction existing,
            Instant normalizedSoldAt,
            SaleTransactionCreateRequest request
    ) {
        if (!hasSamePayload(existing, normalizedSoldAt, request)) {
            throw transactionConflict();
        }
        return new SaleTransactionResult(SaleTransactionResponse.from(existing), false);
    }

    private void validateUniqueLineNumbers(java.util.List<SaleItemRequest> items) {
        Set<Integer> lineNumbers = new HashSet<>();
        for (SaleItemRequest item : items) {
            if (!lineNumbers.add(item.lineNo())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "판매 항목의 lineNo는 중복될 수 없습니다.");
            }
        }
    }

    private boolean hasSamePayload(
            SaleTransaction existing,
            Instant normalizedSoldAt,
            SaleTransactionCreateRequest request
    ) {
        if (!normalizeDatabaseInstant(existing.getSoldAt()).equals(normalizedSoldAt)
                || existing.getItems().size() != request.items().size()) {
            return false;
        }

        Map<Integer, SaleItem> existingByLineNumber = new HashMap<>();
        for (SaleItem item : existing.getItems()) {
            existingByLineNumber.put(item.getLineNo(), item);
        }

        for (SaleItemRequest item : request.items()) {
            SaleItem existingItem = existingByLineNumber.get(item.lineNo());
            if (existingItem == null
                    || !existingItem.getProduct().getId().equals(item.productId())
                    || existingItem.getQuantity() != item.quantity()
                    || existingItem.getPaidAmount() != item.paidAmount()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeClientTransactionId(String clientTransactionId) {
        if (clientTransactionId == null || clientTransactionId.isBlank()) {
            return null;
        }
        return clientTransactionId.trim();
    }

    private Instant normalizeDatabaseInstant(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private ApiException transactionConflict() {
        return new ApiException(ErrorCode.SALE_TRANSACTION_CONFLICT);
    }

    private ApiException transactionConflict(Throwable cause) {
        return new ApiException(
                ErrorCode.SALE_TRANSACTION_CONFLICT,
                ErrorCode.SALE_TRANSACTION_CONFLICT.defaultMessage(),
                cause
        );
    }
}
