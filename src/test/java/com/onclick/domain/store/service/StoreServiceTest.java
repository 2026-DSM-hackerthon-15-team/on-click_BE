package com.onclick.domain.store.service;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.domain.store.dto.CreateStoreRequest;
import com.onclick.domain.store.dto.UpdateStoreRequest;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.domain.store.repository.UserStoreMembershipRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.onclick.global.security.JwtUserIdResolver;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserStoreMembershipRepository membershipRepository;
    @Mock
    private StoreAccessValidator storeAccessValidator;
    @Mock
    private JwtUserIdResolver userIdResolver;
    @Mock
    private Jwt jwt;

    private StoreService storeService;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(
                userRepository,
                storeRepository,
                membershipRepository,
                storeAccessValidator,
                new StoreInputValidator(),
                userIdResolver
        );
    }

    @Test
    void listsOnlyAuthenticatedUsersMemberships() {
        Store store = store(9L, "1호점", "Asia/Seoul");
        UserStoreMembership membership = new UserStoreMembership(
                new User("owner01", "hash"), store, StoreRole.OWNER);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(membershipRepository.findAllByUserIdOrderByStoreIdAsc(5L))
                .thenReturn(List.of(membership));

        var stores = storeService.getStores(jwt);

        assertThat(stores).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(9L);
            assertThat(response.name()).isEqualTo("1호점");
            assertThat(response.role()).isEqualTo(StoreRole.OWNER);
        });
    }

    @Test
    void createsAdditionalStoreWithOwnerMembership() {
        User user = new User("owner01", "hash");
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            ReflectionTestUtils.setField(store, "id", 10L);
            return store;
        });
        when(membershipRepository.save(any(UserStoreMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = storeService.createStore(jwt, new CreateStoreRequest(" 2호점 ", "UTC"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("2호점");
        assertThat(response.timeZone()).isEqualTo("UTC");
        assertThat(response.role()).isEqualTo(StoreRole.OWNER);

        ArgumentCaptor<UserStoreMembership> captor =
                ArgumentCaptor.forClass(UserStoreMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(StoreRole.OWNER);
    }

    @Test
    void onlyOwnerCanUpdateStoreAndTimeZoneIsValidated() {
        Store store = store(9L, "1호점", "Asia/Seoul");
        UserStoreMembership membership = new UserStoreMembership(
                new User("owner01", "hash"), store, StoreRole.OWNER);
        when(storeAccessValidator.requireOwner(jwt, 9L)).thenReturn(membership);

        var response = storeService.updateStore(
                jwt, 9L, new UpdateStoreRequest(" 변경점 ", "UTC"));

        assertThat(response.name()).isEqualTo("변경점");
        assertThat(response.timeZone()).isEqualTo("UTC");
    }

    @Test
    void rejectsEmptyPatchBeforeAccessingStore() {
        assertThatThrownBy(() -> storeService.updateStore(
                jwt, 9L, new UpdateStoreRequest(null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(storeAccessValidator, never()).requireOwner(any(), any());
    }

    private Store store(Long id, String name, String timeZone) {
        Store store = new Store(name, timeZone);
        ReflectionTestUtils.setField(store, "id", id);
        return store;
    }
}
