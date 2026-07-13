package com.onclick.domain.sale.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.entity.SaleStatus;

public record SaleTransactionResponse(
        Long storeId,
        String transactionId,
        Instant soldAt,
        int totalQuantity,
        long totalPaidAmount,
        SaleStatus status,
        Instant createdAt,
        Instant cancelledAt,
        List<SaleLineResponse> items
) {

    public static SaleTransactionResponse from(List<Sale> sales) {
        if (sales.isEmpty()) {
            throw new IllegalArgumentException("sales must not be empty");
        }

        List<Sale> orderedSales = sales.stream()
                .sorted(Comparator.comparingInt(Sale::getLineNo))
                .toList();
        Sale first = orderedSales.getFirst();
        int totalQuantity = orderedSales.stream().mapToInt(Sale::getQuantity).sum();
        long totalPaidAmount = orderedSales.stream().mapToLong(Sale::getPaidAmount).sum();
        SaleStatus status = orderedSales.stream().allMatch(sale -> sale.getStatus() == SaleStatus.CANCELLED)
                ? SaleStatus.CANCELLED
                : SaleStatus.COMPLETED;
        Instant createdAt = orderedSales.stream()
                .map(Sale::getCreatedAt)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant cancelledAt = orderedSales.stream()
                .map(Sale::getCancelledAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new SaleTransactionResponse(
                first.getStoreId(),
                first.getTransactionId(),
                first.getSoldAt(),
                totalQuantity,
                totalPaidAmount,
                status,
                createdAt,
                cancelledAt,
                orderedSales.stream().map(SaleLineResponse::from).toList()
        );
    }
}
