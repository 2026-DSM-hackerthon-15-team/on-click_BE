package com.onclick.domain.instagram.service;

import com.onclick.domain.instagram.dto.InstagramAccountResponse;
import com.onclick.domain.instagram.dto.InstagramAccountUpsertRequest;
import com.onclick.domain.instagram.dto.InstagramCredentialsResponse;
import com.onclick.domain.instagram.entity.InstagramAccount;
import com.onclick.domain.instagram.repository.InstagramAccountRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstagramAccountService {

    private final InstagramAccountRepository repository;
    private final StoreAccessValidator storeAccessValidator;

    @Transactional
    public InstagramAccountResponse upsert(
            Jwt jwt,
            Long storeId,
            InstagramAccountUpsertRequest request
    ) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        try {
            InstagramAccount account = repository.findByStore_Id(storeId)
                    .orElseGet(() -> new InstagramAccount(store, request.accountId(), request.password()));
            if (account.getId() != null) {
                account.updateCredentials(request.accountId(), request.password());
            }
            return InstagramAccountResponse.from(repository.save(account));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public InstagramCredentialsResponse getCredentials(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        InstagramAccount account = repository.findByStore_Id(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTAGRAM_ACCOUNT_NOT_FOUND));
        return InstagramCredentialsResponse.from(account);
    }

    @Transactional(readOnly = true)
    public BrowserCredentials requireCredentials(Long storeId) {
        InstagramAccount account = repository.findByStore_Id(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTAGRAM_ACCOUNT_NOT_FOUND));
        return new BrowserCredentials(account.getAccountId(), account.revealPassword());
    }

    public record BrowserCredentials(String accountId, String password) {
        @Override
        public String toString() {
            return "BrowserCredentials[accountId=" + accountId + ", password=***]";
        }
    }
}
