package com.onclick.domain.sale.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onclick.common.time.KoreanTime;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionPageResponse;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.entity.SaleItem;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SaleTransactionExecutor transactionExecutor;
    private final SaleTransactionRepository saleTransactionRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SaleTransactionPageResponse findTransactions(
            Jwt jwt,
            Long storeId,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        storeAccessValidator.validate(jwt, storeId);
        validatePageRequest(page, size);
        SaleSort saleSort = resolveSort(sortBy, sortDirection);
        Sort sort = Sort.by(saleSort.direction(), saleSort.entityProperty());
        if (!"id".equals(saleSort.entityProperty())) {
            sort = sort.and(Sort.by(saleSort.direction(), "id"));
        }

        Page<Long> transactionIds = saleTransactionRepository.findPageIdsByStoreId(
                storeId,
                PageRequest.of(page, size, sort)
        );
        List<SaleTransactionResponse> content = loadTransactions(
                storeId,
                transactionIds.getContent()
        );
        return SaleTransactionPageResponse.from(
                transactionIds,
                content,
                saleSort.apiField(),
                saleSort.direction().name().toLowerCase(Locale.ROOT)
        );
    }

    public SaleTransactionResult createTransaction(
            Jwt jwt,
            Long storeId,
            SaleTransactionCreateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        String clientTransactionId = normalizeClientTransactionId(request.clientTransactionId());
        LocalDateTime soldAt = normalizeDatabaseDateTime(request.soldAt());
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
                normalizeDatabaseDateTime(KoreanTime.now(clock))
        );
        return SaleTransactionResponse.from(transaction);
    }

    private SaleTransactionResult existingResult(
            SaleTransaction existing,
            LocalDateTime normalizedSoldAt,
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

    private List<SaleTransactionResponse> loadTransactions(
            Long storeId,
            List<Long> transactionIds
    ) {
        if (transactionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, SaleTransaction> transactionsById = saleTransactionRepository
                .findAllWithItemsByStoreIdAndIdIn(storeId, transactionIds)
                .stream()
                .collect(Collectors.toMap(SaleTransaction::getId, Function.identity()));
        return transactionIds.stream()
                .map(transactionsById::get)
                .map(SaleTransactionResponse::from)
                .toList();
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "page는 0 이상, size는 1 이상 100 이하여야 합니다."
            );
        }
    }

    private SaleSort resolveSort(String sortBy, String sortDirection) {
        String normalizedSortBy = sortBy == null
                ? "soldat"
                : sortBy.trim().toLowerCase(Locale.ROOT);
        String normalizedDirection = sortDirection == null
                ? "desc"
                : sortDirection.trim().toLowerCase(Locale.ROOT);

        SaleSortField sortField = switch (normalizedSortBy) {
            case "soldat" -> new SaleSortField("soldAt", "soldAt");
            case "createdat" -> new SaleSortField("createdAt", "createdAt");
            case "saleid" -> new SaleSortField("saleId", "id");
            case "status" -> new SaleSortField("status", "status");
            default -> throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "sortBy는 soldAt, createdAt, saleId, status 중 하나여야 합니다."
            );
        };
        Sort.Direction direction = switch (normalizedDirection) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "sortDirection은 asc 또는 desc여야 합니다."
            );
        };
        return new SaleSort(sortField.apiField(), sortField.entityProperty(), direction);
    }

    private record SaleSortField(String apiField, String entityProperty) {
    }

    private record SaleSort(String apiField, String entityProperty, Sort.Direction direction) {
    }

    private boolean hasSamePayload(
            SaleTransaction existing,
            LocalDateTime normalizedSoldAt,
            SaleTransactionCreateRequest request
    ) {
        if (!normalizeDatabaseDateTime(existing.getSoldAt()).equals(normalizedSoldAt)
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

    private LocalDateTime normalizeDatabaseDateTime(LocalDateTime value) {
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
