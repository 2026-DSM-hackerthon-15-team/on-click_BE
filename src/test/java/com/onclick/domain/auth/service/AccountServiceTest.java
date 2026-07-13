package com.onclick.domain.auth.service;

import java.util.Optional;

import com.onclick.domain.auth.dto.ChangePasswordRequest;
import com.onclick.domain.auth.dto.UpdateAccountRequest;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUserIdResolver userIdResolver;
    @Mock
    private Jwt jwt;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                userRepository,
                passwordEncoder,
                userIdResolver,
                new UserInputValidator()
        );
    }

    @Test
    void getsAuthenticatedUsersProfile() {
        User user = user();
        when(userIdResolver.resolve(jwt)).thenReturn(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));

        var response = accountService.getProfile(jwt);

        assertThat(response.userId()).isEqualTo(11L);
        assertThat(response.accountId()).isEqualTo("owner01");
        assertThat(response.name()).isEqualTo("기존 이름");
        assertThat(response.email()).isEqualTo("old@example.com");
    }

    @Test
    void updatesProfileAfterVerifyingPasswordAndNormalizesValues() {
        User user = user();
        when(userIdResolver.resolve(jwt)).thenReturn(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);

        var response = accountService.updateProfile(jwt, new UpdateAccountRequest(
                "password123",
                " owner02 ",
                " 새 이름 ",
                " NEW@Example.com "
        ));

        assertThat(response.accountId()).isEqualTo("owner02");
        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.email()).isEqualTo("new@example.com");
        verify(userRepository).existsByAccountIdAndIdNot("owner02", 11L);
        verify(userRepository).existsByEmailAndIdNot("new@example.com", 11L);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void rejectsProfileUpdateWhenCurrentPasswordDoesNotMatch() {
        User user = user();
        when(userIdResolver.resolve(jwt)).thenReturn(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.updateProfile(jwt, new UpdateAccountRequest(
                "wrong-password",
                null,
                "새 이름",
                null
        )))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(userRepository, never()).saveAndFlush(user);
    }

    @Test
    void rejectsDuplicateEmailWithoutChangingProfile() {
        User user = user();
        when(userIdResolver.resolve(jwt)).thenReturn(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(userRepository.existsByEmailAndIdNot("used@example.com", 11L)).thenReturn(true);

        assertThatThrownBy(() -> accountService.updateProfile(jwt, new UpdateAccountRequest(
                "password123",
                null,
                null,
                "used@example.com"
        )))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        assertThat(user.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).saveAndFlush(user);
    }

    @Test
    void changesPasswordOnlyAfterVerifyingCurrentPassword() {
        User user = user();
        when(userIdResolver.resolve(jwt)).thenReturn(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password123")).thenReturn("new-bcrypt-hash");

        accountService.changePassword(jwt, new ChangePasswordRequest(
                "password123",
                "new-password123"
        ));

        assertThat(user.getPasswordHash()).isEqualTo("new-bcrypt-hash");
    }

    private User user() {
        User user = new User("owner01", "bcrypt-hash", "기존 이름", "old@example.com");
        ReflectionTestUtils.setField(user, "id", 11L);
        return user;
    }
}
