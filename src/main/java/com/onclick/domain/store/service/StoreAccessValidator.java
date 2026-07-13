package com.onclick.domain.store.service;

import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StoreAccessValidator {

    private final StoreRepository storeRepository;
    private final JwtUserIdResolver userIdResolver;

    public StoreAccessValidator(
            StoreRepository storeRepository,
            JwtUserIdResolver userIdResolver
    ) {
        this.storeRepository = storeRepository;
        this.userIdResolver = userIdResolver;
    }

    @Transactional(readOnly = true)
    public Store validate(Jwt jwt, Long storeId) {
        return validate(userIdResolver.resolve(jwt), storeId);
    }

    @Transactional(readOnly = true)
    public Store validate(long userId, Long storeId) {
        if (storeId == null || storeId <= 0) {
            throw new ApiException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return storeRepository.findByIdAndOwnerId(storeId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_ACCESS_DENIED));
    }
}
