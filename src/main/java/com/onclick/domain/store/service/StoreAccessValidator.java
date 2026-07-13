package com.onclick.domain.store.service;

import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StoreAccessValidator {

    private final UserStoreMembershipRepository membershipRepository;
    private final JwtUserIdResolver userIdResolver;

    public StoreAccessValidator(
            UserStoreMembershipRepository membershipRepository,
            JwtUserIdResolver userIdResolver
    ) {
        this.membershipRepository = membershipRepository;
        this.userIdResolver = userIdResolver;
    }

    @Transactional(readOnly = true)
    public UserStoreMembership validate(Jwt jwt, Long storeId) {
        return validate(userIdResolver.resolve(jwt), storeId);
    }

    @Transactional(readOnly = true)
    public UserStoreMembership validate(long userId, Long storeId) {
        if (storeId == null || storeId <= 0) {
            throw new ApiException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return membershipRepository.findByUserIdAndStoreId(userId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_ACCESS_DENIED));
    }

    @Transactional(readOnly = true)
    public UserStoreMembership requireOwner(Jwt jwt, Long storeId) {
        UserStoreMembership membership = validate(jwt, storeId);
        if (membership.getRole() != StoreRole.OWNER) {
            throw new ApiException(ErrorCode.STORE_OWNER_REQUIRED);
        }
        return membership;
    }
}
