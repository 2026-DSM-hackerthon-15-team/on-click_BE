package com.onclick.domain.sale.dto;

import com.onclick.domain.sale.entity.SaleItem;

public record SaleItemResponse(
        Long id,
        int lineNo,
        Long productId,
        String productName,
        long productPrice,
        int quantity,
        long paidAmount
) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getProductPriceSnapshot(),
                item.getQuantity(),
                item.getPaidAmount()
        );
    }
}
