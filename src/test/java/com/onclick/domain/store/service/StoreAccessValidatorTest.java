package com.onclick.domain.store.service;

import java.util.Optional;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAccessValidatorTest {

    @Mock
    private UserStoreMembershipRepository membershipRepository;
    @Mock
    private JwtUserIdResolver userIdResolver;
    @Mock
    private Jwt jwt;

    private StoreAccessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StoreAccessValidator(membershipRepository, userIdResolver);
    }

    @Test
    void validatesPathStoreIdAgainstAuthenticatedUserMembership() {
        UserStoreMembership membership = membership(StoreRole.MANAGER);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(membershipRepository.findByUserIdAndStoreId(5L, 9L)).thenReturn(Optional.of(membership));

        assertThat(validator.validate(jwt, 9L)).isSameAs(membership);
    }

    @Test
    void hidesUnrelatedStoreAsAccessDenied() {
        when(membershipRepository.findByUserIdAndStoreId(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(5L, 9L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.STORE_ACCESS_DENIED));
    }

    @Test
    void managerCannotPerformOwnerOnlyOperation() {
        UserStoreMembership membership = membership(StoreRole.MANAGER);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(membershipRepository.findByUserIdAndStoreId(5L, 9L)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> validator.requireOwner(jwt, 9L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.STORE_OWNER_REQUIRED));
    }

    private UserStoreMembership membership(StoreRole role) {
        return new UserStoreMembership(
                new User("owner01", "hash"),
                new Store("1호점", "Asia/Seoul"),
                role
        );
    }
}
