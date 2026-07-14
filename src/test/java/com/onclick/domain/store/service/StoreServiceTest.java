package com.onclick.domain.store.service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.auth.repository.UserRepository;
import com.onclick.domain.store.dto.CreateStoreRequest;
import com.onclick.domain.store.dto.UpdateStoreRequest;
import com.onclick.domain.store.entity.Industry;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
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
                storeAccessValidator,
                new StoreInputValidator(),
                userIdResolver
        );
    }

    @Test
    void listsOnlyStoresOwnedByAuthenticatedUser() {
        User owner = user(5L);
        Store store = store(9L, owner, "1호점", Industry.CAFE, "서울 강남구");
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(storeRepository.findAllByOwnerIdOrderByIdAsc(5L)).thenReturn(List.of(store));

        var stores = storeService.getStores(jwt);

        assertThat(stores).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(9L);
            assertThat(response.name()).isEqualTo("1호점");
            assertThat(response.industry()).isEqualTo(Industry.CAFE);
            assertThat(response.roadAddress()).isEqualTo("서울 강남구");
            assertThat(response.closingTime()).isEqualTo(LocalTime.of(22, 0));
        });
    }

    @Test
    void createsAdditionalStoreOwnedByAuthenticatedUser() {
        User owner = user(5L);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(owner));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            ReflectionTestUtils.setField(store, "id", 10L);
            return store;
        });

        var response = storeService.createStore(
                jwt,
                new CreateStoreRequest(
                        " 2호점 ",
                        Industry.RESTAURANT,
                        " 서울 마포구 ",
                        LocalTime.of(21, 30)
                )
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("2호점");
        assertThat(response.industry()).isEqualTo(Industry.RESTAURANT);
        assertThat(response.roadAddress()).isEqualTo("서울 마포구");
        assertThat(response.closingTime()).isEqualTo(LocalTime.of(21, 30));

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(owner);
        assertThat(captor.getValue().getIndustry()).isEqualTo(Industry.RESTAURANT);
        assertThat(captor.getValue().getRoadAddress()).isEqualTo("서울 마포구");
        assertThat(captor.getValue().getClosingTime()).isEqualTo(LocalTime.of(21, 30));
    }

    @Test
    void createsStoreWithDefaultIndustryAndNullableRoadAddress() {
        User owner = user(5L);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(owner));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = storeService.createStore(
                jwt,
                new CreateStoreRequest("2호점", null, null, null)
        );

        assertThat(response.industry()).isEqualTo(Industry.OTHER);
        assertThat(response.roadAddress()).isNull();
        assertThat(response.closingTime()).isEqualTo(Store.DEFAULT_CLOSING_TIME);
    }

    @Test
    void rejectsBlankRoadAddressWhenCreatingStore() {
        User owner = user(5L);
        when(userIdResolver.resolve(jwt)).thenReturn(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> storeService.createStore(
                jwt,
                new CreateStoreRequest("2호점", Industry.CAFE, "   ", null)
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(storeRepository, never()).save(any());
    }

    @Test
    void updatesStoreOnlyAfterOwnerValidation() {
        Store store = store(9L, user(5L), "1호점", Industry.CAFE, "서울 강남구");
        when(storeAccessValidator.validate(jwt, 9L)).thenReturn(store);

        var response = storeService.updateStore(
                jwt,
                9L,
                new UpdateStoreRequest(
                        " 변경점 ",
                        Industry.RETAIL,
                        " 부산 해운대구 ",
                        LocalTime.of(20, 45)
                )
        );

        assertThat(response.name()).isEqualTo("변경점");
        assertThat(response.industry()).isEqualTo(Industry.RETAIL);
        assertThat(response.roadAddress()).isEqualTo("부산 해운대구");
        assertThat(response.closingTime()).isEqualTo(LocalTime.of(20, 45));
    }

    @Test
    void rejectsBlankRoadAddressWhenUpdatingStore() {
        Store store = store(9L, user(5L), "1호점", Industry.CAFE, "서울 강남구");
        when(storeAccessValidator.validate(jwt, 9L)).thenReturn(store);

        assertThatThrownBy(() -> storeService.updateStore(
                jwt,
                9L,
                new UpdateStoreRequest(null, null, "   ", null)
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThat(store.getRoadAddress()).isEqualTo("서울 강남구");
    }

    @Test
    void rejectsEmptyPatchBeforeAccessingStore() {
        assertThatThrownBy(() -> storeService.updateStore(
                jwt, 9L, new UpdateStoreRequest(null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(storeAccessValidator, never()).validate(any(), any());
    }

    private User user(Long id) {
        User user = new User("owner01", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Store store(Long id, User owner, String name, Industry industry, String roadAddress) {
        Store store = new Store(owner, name);
        ReflectionTestUtils.setField(store, "id", id);
        ReflectionTestUtils.setField(store, "industry", industry);
        ReflectionTestUtils.setField(store, "roadAddress", roadAddress);
        return store;
    }
}
