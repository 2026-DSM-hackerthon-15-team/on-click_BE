package com.onclick.domain.sale.dto;

import java.time.Instant;

import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.entity.SaleStatus;

public record SaleLineResponse(
        Long id,
        int lineNo,
        Long productId,
        String productName,
        long productPrice,
        int quantity,
        long paidAmount,
        SaleStatus status,
        Instant cancelledAt
) {

    public static SaleLineResponse from(Sale sale) {
        return new SaleLineResponse(
                sale.getId(),
                sale.getLineNo(),
                sale.getProduct().getId(),
                sale.getProductNameSnapshot(),
                sale.getProductPriceSnapshot(),
                sale.getQuantity(),
                sale.getPaidAmount(),
                sale.getStatus(),
                sale.getCancelledAt()
        );
    }
}
