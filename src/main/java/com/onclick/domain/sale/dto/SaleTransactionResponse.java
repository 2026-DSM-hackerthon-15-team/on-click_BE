package com.onclick.domain.sale.dto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.onclick.domain.sale.entity.SaleItem;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;

public record SaleTransactionResponse(
        Long saleId,
        Long storeId,
        String clientTransactionId,
        LocalDateTime soldAt,
        int totalQuantity,
        long totalPaidAmount,
        SaleStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        List<SaleItemResponse> items
) {

    public static SaleTransactionResponse from(SaleTransaction transaction) {
        List<SaleItem> orderedItems = transaction.getItems().stream()
                .sorted(Comparator.comparingInt(SaleItem::getLineNo))
                .toList();
        return new SaleTransactionResponse(
                transaction.getId(),
                transaction.getStoreId(),
                transaction.getClientTransactionId(),
                transaction.getSoldAt(),
                transaction.totalQuantity(),
                transaction.totalPaidAmount(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getCancelledAt(),
                orderedItems.stream().map(SaleItemResponse::from).toList()
        );
    }
}
