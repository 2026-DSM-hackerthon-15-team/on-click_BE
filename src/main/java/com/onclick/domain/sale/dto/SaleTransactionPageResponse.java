package com.onclick.domain.sale.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record SaleTransactionPageResponse(
        List<SaleTransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        String sortBy,
        String sortDirection
) {

    public SaleTransactionPageResponse {
        content = List.copyOf(content);
    }

    public static SaleTransactionPageResponse from(
            Page<?> resultPage,
            List<SaleTransactionResponse> content,
            String sortBy,
            String sortDirection
    ) {
        return new SaleTransactionPageResponse(
                content,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext(),
                sortBy,
                sortDirection
        );
    }
}
