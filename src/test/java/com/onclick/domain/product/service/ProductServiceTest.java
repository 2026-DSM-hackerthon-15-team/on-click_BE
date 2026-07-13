package com.onclick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.product.dto.ProductCreateRequest;
import com.onclick.domain.product.dto.ProductStatusUpdateRequest;
import com.onclick.domain.product.dto.ProductUpdateRequest;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private Jwt jwt;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, storeAccessValidator);
    }

    @Test
    void createsProductInRequestedStore() {
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 10L);
            return product;
        });

        var response = productService.create(jwt, 3L, new ProductCreateRequest(" 아메리카노 ", 4_500L));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.storeId()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("아메리카노");
        assertThat(response.price()).isEqualTo(4_500);
        assertThat(response.active()).isTrue();
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void listsOnlyProductsReturnedForTheRequestedStore() {
        Product product = product(7L, 3L, "라테", 5_000);
        given(productRepository.findAllByStoreIdOrderByCreatedAtDesc(3L)).willReturn(List.of(product));

        var response = productService.findAll(jwt, 3L);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.storeId()).isEqualTo(3L);
        });
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void updatesProductNameAndPrice() {
        Product product = product(7L, 3L, "라테", 5_000);
        given(productRepository.findByIdAndStoreId(7L, 3L)).willReturn(Optional.of(product));

        var response = productService.update(
                jwt,
                3L,
                7L,
                new ProductUpdateRequest(" 바닐라 라테 ", 5_500L)
        );

        assertThat(response.name()).isEqualTo("바닐라 라테");
        assertThat(response.price()).isEqualTo(5_500L);
    }

    @Test
    void rejectsEmptyPatch() {
        assertThatThrownBy(() -> productService.update(
                jwt,
                3L,
                7L,
                new ProductUpdateRequest(null, null)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void changesProductStatus() {
        Product product = product(7L, 3L, "라테", 5_000);
        given(productRepository.findByIdAndStoreId(7L, 3L)).willReturn(Optional.of(product));

        var response = productService.updateStatus(
                jwt,
                3L,
                7L,
                new ProductStatusUpdateRequest(false)
        );

        assertThat(response.active()).isFalse();
    }

    @Test
    void cannotAccessAProductOutsideTheStore() {
        given(productRepository.findByIdAndStoreId(7L, 3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateStatus(
                jwt,
                3L,
                7L,
                new ProductStatusUpdateRequest(false)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    private Product product(Long id, Long storeId, String name, long price) {
        Product product = Product.create(storeId, name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
