package com.onclick.domain.store.service;

import java.util.List;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.domain.store.dto.CreateStoreRequest;
import com.onclick.domain.store.dto.StoreResponse;
import com.onclick.domain.store.dto.UpdateStoreRequest;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final UserStoreMembershipRepository membershipRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final StoreInputValidator storeInputValidator;
    private final JwtUserIdResolver userIdResolver;

    public StoreService(
            UserRepository userRepository,
            StoreRepository storeRepository,
            UserStoreMembershipRepository membershipRepository,
            StoreAccessValidator storeAccessValidator,
            StoreInputValidator storeInputValidator,
            JwtUserIdResolver userIdResolver
    ) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.membershipRepository = membershipRepository;
        this.storeAccessValidator = storeAccessValidator;
        this.storeInputValidator = storeInputValidator;
        this.userIdResolver = userIdResolver;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> getStores(Jwt jwt) {
        long userId = userIdResolver.resolve(jwt);
        return membershipRepository.findAllByUserIdOrderByStoreIdAsc(userId).stream()
                .map(StoreResponse::from)
                .toList();
    }

    @Transactional
    public StoreResponse createStore(Jwt jwt, CreateStoreRequest request) {
        long userId = userIdResolver.resolve(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        Store store = storeRepository.save(new Store(
                storeInputValidator.requireName(request.name()),
                storeInputValidator.normalizeTimeZone(request.timeZone())
        ));
        UserStoreMembership membership = membershipRepository.save(
                new UserStoreMembership(user, store, StoreRole.OWNER)
        );
        return StoreResponse.from(membership);
    }

    @Transactional
    public StoreResponse updateStore(Jwt jwt, Long storeId, UpdateStoreRequest request) {
        if (request.name() == null && request.timeZone() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 지점 정보를 입력해 주세요.");
        }

        UserStoreMembership membership = storeAccessValidator.requireOwner(jwt, storeId);
        String name = request.name() == null ? null : storeInputValidator.requireName(request.name());
        String timeZone = request.timeZone() == null
                ? null
                : storeInputValidator.requireTimeZone(request.timeZone());
        membership.getStore().update(name, timeZone);
        return StoreResponse.from(membership);
    }
}
