package com.onclick.domain.auth.service;

import java.util.Optional;
import java.time.Instant;

import com.onclick.domain.auth.dto.LoginRequest;
import com.onclick.domain.auth.dto.SignUpRequest;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;
import com.onclick.domain.store.service.StoreInputValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.IssuedAccessToken;
import com.onclick.global.security.JwtTokenProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserStoreMembershipRepository membershipRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                storeRepository,
                membershipRepository,
                passwordEncoder,
                jwtTokenProvider,
                new StoreInputValidator()
        );
    }

    @Test
    void signUpCreatesUserInitialStoreAndOwnerMembershipWithoutToken() {
        when(userRepository.existsByAccountId("owner01")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 11L);
            ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-07-13T01:00:00Z"));
            return user;
        });
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            ReflectionTestUtils.setField(store, "id", 21L);
            return store;
        });
        var response = authService.signUp(new SignUpRequest(
                " owner01 ", "password123", " 1호점 ", null));

        assertThat(response.userId()).isEqualTo(11L);
        assertThat(response.accountId()).isEqualTo("owner01");
        assertThat(response.storeId()).isEqualTo(21L);
        assertThat(response.storeName()).isEqualTo("1호점");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-13T01:00:00Z"));
        verify(jwtTokenProvider, never()).issue(any());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountId()).isEqualTo("owner01");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");

        ArgumentCaptor<UserStoreMembership> membershipCaptor =
                ArgumentCaptor.forClass(UserStoreMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(StoreRole.OWNER);
        assertThat(membershipCaptor.getValue().getStore().getTimeZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void signUpRejectsDuplicateAccountIdBeforeEncodingPassword() {
        when(userRepository.existsByAccountId("owner01")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(
                new SignUpRequest("owner01", "password123", "1호점", null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.LOGIN_ID_ALREADY_EXISTS));

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void loginVerifiesPasswordAndIssuesAccessToken() {
        User user = new User("owner01", "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", 11L);
        when(userRepository.findByAccountId("owner01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(jwtTokenProvider.issue(11L)).thenReturn(new IssuedAccessToken("access-token", 3600));

        var response = authService.login(new LoginRequest("owner01", "password123"));

        assertThat(response.userId()).isEqualTo(11L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginDoesNotIssueTokenWhenPasswordDoesNotMatch() {
        User user = new User("owner01", "bcrypt-hash");
        when(userRepository.findByAccountId("owner01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("owner01", "wrong-password")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(jwtTokenProvider, never()).issue(any());
    }
}
