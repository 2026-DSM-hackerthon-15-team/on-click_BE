package com.onclick.domain.instagram.service;

import java.util.Optional;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.instagram.dto.InstagramAccountUpsertRequest;
import com.onclick.domain.instagram.entity.InstagramAccount;
import com.onclick.domain.instagram.repository.InstagramAccountRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InstagramAccountServiceTest {

    @Mock InstagramAccountRepository repository;
    @Mock StoreAccessValidator storeAccessValidator;
    @Mock Jwt jwt;

    InstagramAccountService service;

    @BeforeEach
    void setUp() {
        service = new InstagramAccountService(repository, storeAccessValidator);
    }

    @Test
    void createsAccountForOwnedStoreAndPreservesPlaintextPassword() {
        Store store = store(3L);
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store);
        given(repository.findByStore_Id(3L)).willReturn(Optional.empty());
        given(repository.save(any(InstagramAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(
                jwt,
                3L,
                new InstagramAccountUpsertRequest("  cafe.owner  ", " password with spaces ")
        );

        assertThat(response.accountId()).isEqualTo("cafe.owner");
        ArgumentCaptor<InstagramAccount> captor = ArgumentCaptor.forClass(InstagramAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStore()).isSameAs(store);
        assertThat(captor.getValue().revealPassword()).isEqualTo(" password with spaces ");
    }

    @Test
    void updatesExistingAccountInsteadOfCreatingAnotherOne() {
        Store store = store(3L);
        InstagramAccount account = new InstagramAccount(store, "old-id", "old-password");
        ReflectionTestUtils.setField(account, "id", 7L);
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store);
        given(repository.findByStore_Id(3L)).willReturn(Optional.of(account));
        given(repository.save(account)).willReturn(account);

        service.upsert(jwt, 3L, new InstagramAccountUpsertRequest("new-id", "new-password"));

        assertThat(account.getAccountId()).isEqualTo("new-id");
        assertThat(account.revealPassword()).isEqualTo("new-password");
        verify(repository).save(account);
    }

    @Test
    void returnsPlaintextCredentialsOnlyAfterOwnerValidation() {
        Store store = store(3L);
        InstagramAccount account = new InstagramAccount(store, "owner-id", "plain-password");
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store);
        given(repository.findByStore_Id(3L)).willReturn(Optional.of(account));

        var response = service.getCredentials(jwt, 3L);

        verify(storeAccessValidator).validate(jwt, 3L);
        assertThat(response.accountId()).isEqualTo("owner-id");
        assertThat(response.password()).isEqualTo("plain-password");
        assertThat(response.toString()).doesNotContain("plain-password");
    }

    @Test
    void rejectsMissingAccountForOwnedStore() {
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store(3L));
        given(repository.findByStore_Id(3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCredentials(jwt, 3L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INSTAGRAM_ACCOUNT_NOT_FOUND));
    }

    private Store store(Long id) {
        User owner = new User("owner-id", "password-hash");
        ReflectionTestUtils.setField(owner, "id", 1L);
        Store store = new Store(owner, "매장");
        ReflectionTestUtils.setField(store, "id", id);
        return store;
    }
}
