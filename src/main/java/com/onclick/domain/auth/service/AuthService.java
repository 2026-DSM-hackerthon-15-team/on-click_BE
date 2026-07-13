package com.onclick.domain.auth.service;

import com.onclick.domain.auth.dto.AuthTokenResponse;
import com.onclick.domain.auth.dto.LoginRequest;
import com.onclick.domain.auth.dto.SignUpRequest;
import com.onclick.domain.auth.dto.SignUpResponse;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.domain.store.service.StoreInputValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.IssuedAccessToken;
import com.onclick.global.security.JwtTokenProvider;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StoreInputValidator storeInputValidator;

    public AuthService(
            UserRepository userRepository,
            StoreRepository storeRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            StoreInputValidator storeInputValidator
    ) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.storeInputValidator = storeInputValidator;
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        String accountId = request.accountId().trim();
        if (userRepository.existsByAccountId(accountId)) {
            throw new ApiException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }

        User user;
        try {
            user = userRepository.saveAndFlush(new User(accountId, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }

        String storeName = storeInputValidator.requireName(request.storeName());
        String timeZone = storeInputValidator.normalizeTimeZone(request.timeZone());
        Store store = storeRepository.save(new Store(user, storeName, timeZone));

        return new SignUpResponse(
                user.getId(),
                user.getAccountId(),
                store.getId(),
                store.getName(),
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByAccountId(request.accountId().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        IssuedAccessToken token = jwtTokenProvider.issue(user.getId());
        return AuthTokenResponse.bearer(user.getId(), token.value(), token.expiresInSeconds());
    }
}
