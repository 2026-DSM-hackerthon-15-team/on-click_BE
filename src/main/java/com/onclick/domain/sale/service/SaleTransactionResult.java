package com.onclick.domain.sale.service;

import com.onclick.domain.sale.dto.SaleTransactionResponse;

public record SaleTransactionResult(SaleTransactionResponse transaction, boolean created) {
}
