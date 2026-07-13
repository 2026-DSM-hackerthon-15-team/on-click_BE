package com.onclick.domain.store.service;

import java.util.Optional;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAccessValidatorTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private JwtUserIdResolver userIdResolver;
    @Mock
    private Jwt jwt;

    private StoreAccessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StoreAccessValidator(storeRepository, userIdResolver);
    }

    @Test
    void returnsStoreOwnedByAuthenticatedUser() {
        Store store = new Store(new User("owner01", "hash"), "1호점", "Asia/Seoul");
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(storeRepository.findByIdAndOwnerId(9L, 5L)).thenReturn(Optional.of(store));

        assertThat(validator.validate(jwt, 9L)).isSameAs(store);
    }

    @Test
    void hidesStoreOwnedByAnotherUserAsAccessDenied() {
        when(storeRepository.findByIdAndOwnerId(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(5L, 9L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.STORE_ACCESS_DENIED));
    }

    @Test
    void rejectsInvalidStoreIdWithoutQueryingRepository() {
        assertThatThrownBy(() -> validator.validate(5L, 0L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.STORE_ACCESS_DENIED));

        verify(storeRepository, never()).findByIdAndOwnerId(0L, 5L);
    }
}
