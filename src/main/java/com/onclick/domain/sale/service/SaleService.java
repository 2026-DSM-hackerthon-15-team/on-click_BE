package com.onclick.domain.sale.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.repository.SaleRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final StoreAccessValidator storeAccessValidator;

    public SaleService(
            SaleRepository saleRepository,
            ProductRepository productRepository,
            StoreAccessValidator storeAccessValidator
    ) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.storeAccessValidator = storeAccessValidator;
    }

    @Transactional
    public SaleTransactionResult createTransaction(
            Jwt jwt,
            Long storeId,
            SaleTransactionCreateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        String transactionId = normalizeTransactionId(request.transactionId());
        validateUniqueLineNumbers(request.items());

        List<Sale> existing = saleRepository
                .findAllByStoreIdAndTransactionIdOrderByLineNoAsc(storeId, transactionId);
        if (!existing.isEmpty()) {
            if (!hasSamePayload(existing, request)) {
                throw transactionConflict();
            }
            return new SaleTransactionResult(SaleTransactionResponse.from(existing), false);
        }

        Map<Long, Product> products = loadProducts(storeId, request.items());
        List<Sale> sales = request.items().stream()
                .map(item -> createSale(storeId, transactionId, request.soldAt(), item, products))
                .toList();

        try {
            List<Sale> saved = saleRepository.saveAllAndFlush(sales);
            return new SaleTransactionResult(SaleTransactionResponse.from(saved), true);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    ErrorCode.SALE_TRANSACTION_CONFLICT,
                    ErrorCode.SALE_TRANSACTION_CONFLICT.defaultMessage(),
                    exception
            );
        }
    }

    @Transactional
    public SaleTransactionResponse cancelTransaction(Jwt jwt, Long storeId, String transactionId) {
        storeAccessValidator.validate(jwt, storeId);
        String normalizedTransactionId = normalizeTransactionId(transactionId);
        List<Sale> sales = saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(
                storeId,
                normalizedTransactionId
        );
        if (sales.isEmpty()) {
            throw new ApiException(ErrorCode.SALE_TRANSACTION_NOT_FOUND);
        }

        Instant cancelledAt = Instant.now();
        sales.forEach(sale -> sale.cancel(cancelledAt));
        return SaleTransactionResponse.from(sales);
    }

    private Map<Long, Product> loadProducts(Long storeId, List<SaleItemRequest> items) {
        Set<Long> productIds = items.stream()
                .map(SaleItemRequest::productId)
                .collect(java.util.stream.Collectors.toSet());
        List<Product> products = productRepository.findAllByStoreIdAndIdIn(storeId, productIds);
        if (products.size() != productIds.size()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
            if (!product.isActive()) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "판매 중지된 상품은 판매할 수 없습니다: " + product.getId()
                );
            }
        }
        return productsById;
    }

    private Sale createSale(
            Long storeId,
            String transactionId,
            Instant soldAt,
            SaleItemRequest item,
            Map<Long, Product> products
    ) {
        Product product = products.get(item.productId());
        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return Sale.create(
                storeId,
                transactionId,
                item.lineNo(),
                product,
                item.quantity(),
                item.paidAmount(),
                soldAt
        );
    }

    private void validateUniqueLineNumbers(List<SaleItemRequest> items) {
        Set<Integer> lineNumbers = new HashSet<>();
        for (SaleItemRequest item : items) {
            if (!lineNumbers.add(item.lineNo())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "판매 항목의 lineNo는 중복될 수 없습니다.");
            }
        }
    }

    private boolean hasSamePayload(List<Sale> existing, SaleTransactionCreateRequest request) {
        if (existing.size() != request.items().size()) {
            return false;
        }

        Map<Integer, Sale> existingByLineNumber = new HashMap<>();
        for (Sale sale : existing) {
            if (!sale.getSoldAt().equals(request.soldAt())) {
                return false;
            }
            existingByLineNumber.put(sale.getLineNo(), sale);
        }

        for (SaleItemRequest item : request.items()) {
            Sale sale = existingByLineNumber.get(item.lineNo());
            if (sale == null
                    || !sale.getProduct().getId().equals(item.productId())
                    || sale.getQuantity() != item.quantity()
                    || sale.getPaidAmount() != item.paidAmount()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeTransactionId(String transactionId) {
        String normalized = transactionId == null ? "" : transactionId.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래번호를 입력해 주세요.");
        }
        return normalized;
    }

    private ApiException transactionConflict() {
        return new ApiException(ErrorCode.SALE_TRANSACTION_CONFLICT);
    }
}
