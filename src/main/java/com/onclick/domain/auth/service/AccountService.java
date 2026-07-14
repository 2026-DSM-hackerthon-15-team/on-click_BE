package com.onclick.domain.auth.service;

import com.onclick.domain.auth.dto.AccountProfileResponse;
import com.onclick.domain.auth.dto.ChangePasswordRequest;
import com.onclick.domain.auth.dto.UpdateAccountRequest;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUserIdResolver userIdResolver;
    private final UserInputValidator userInputValidator;

    @Transactional(readOnly = true)
    public AccountProfileResponse getProfile(Jwt jwt) {
        return AccountProfileResponse.from(requireUser(jwt));
    }

    @Transactional
    public AccountProfileResponse updateProfile(Jwt jwt, UpdateAccountRequest request) {
        if (request.accountId() == null && request.name() == null && request.email() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 계정 정보를 입력해 주세요.");
        }

        User user = requireUser(jwt);
        verifyCurrentPassword(request.currentPassword(), user);

        String accountId = request.accountId() == null
                ? null
                : userInputValidator.requireAccountId(request.accountId());
        String name = request.name() == null ? null : userInputValidator.requireName(request.name());
        String email = request.email() == null ? null : userInputValidator.requireEmail(request.email());

        boolean accountIdChanged = accountId != null && !accountId.equals(user.getAccountId());
        boolean emailChanged = email != null && !email.equals(user.getEmail());
        if (accountIdChanged && userRepository.existsByAccountIdAndIdNot(accountId, user.getId())) {
            throw new ApiException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (emailChanged && userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        user.updateProfile(accountId, name, email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            ErrorCode fallback = emailChanged && !accountIdChanged
                    ? ErrorCode.EMAIL_ALREADY_EXISTS
                    : ErrorCode.LOGIN_ID_ALREADY_EXISTS;
            throw DuplicateUserExceptionMapper.map(exception, fallback);
        }
        return AccountProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(Jwt jwt, ChangePasswordRequest request) {
        User user = requireUser(jwt);
        verifyCurrentPassword(request.currentPassword(), user);
        if (request.newPassword() == null
                || request.newPassword().length() < 8
                || request.newPassword().length() > 72) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "새 비밀번호는 8자 이상 72자 이하로 입력해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private User requireUser(Jwt jwt) {
        long userId = userIdResolver.resolve(jwt);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private void verifyCurrentPassword(String currentPassword, User user) {
        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}
