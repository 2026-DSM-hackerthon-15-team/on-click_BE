package com.onclick.domain.sale.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SaleTransactionExecutor {

    private final SaleTransactionRepository saleTransactionRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SaleTransaction> findByClientTransactionId(
            Long storeId,
            String clientTransactionId
    ) {
        return saleTransactionRepository.findByStoreIdAndClientTransactionId(
                storeId,
                clientTransactionId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SaleTransaction create(
            Long storeId,
            String clientTransactionId,
            LocalDateTime soldAt,
            List<SaleItemRequest> items
    ) {
        Map<Long, Product> products = loadProducts(storeId, items);
        SaleTransaction transaction = SaleTransaction.create(storeId, clientTransactionId, soldAt);
        for (SaleItemRequest item : items) {
            transaction.addItem(
                    item.lineNo(),
                    products.get(item.productId()),
                    item.quantity(),
                    item.paidAmount()
            );
        }
        transaction.totalQuantity();
        transaction.totalPaidAmount();
        return saleTransactionRepository.saveAndFlush(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SaleTransaction cancel(Long storeId, Long saleId, LocalDateTime cancelledAt) {
        saleTransactionRepository.cancelIfCompleted(saleId, storeId, cancelledAt);
        return saleTransactionRepository.findByIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.SALE_TRANSACTION_NOT_FOUND));
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
}
