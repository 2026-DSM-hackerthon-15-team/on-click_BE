package com.onclick.common.ai.dto;

public record MarketingGenerationRequest(
        Long storeId,
        String storeName,
        String prompt
) {
}
