package com.onclick.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.repository.SaleRepository;
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
class SaleServiceTest {

    private static final Instant SOLD_AT = Instant.parse("2026-07-13T03:15:00Z");

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private Jwt jwt;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(saleRepository, productRepository, storeAccessValidator);
    }

    @Test
    void savesAllLinesAtomicallyWithProductSnapshots() {
        Product americano = product(10L, 3L, "아메리카노", 4_500);
        Product latte = product(11L, 3L, "라테", 5_000);
        SaleTransactionCreateRequest request = request(
                new SaleItemRequest(1, 10L, 2, 9_000),
                new SaleItemRequest(2, 11L, 1, 4_500)
        );
        given(saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(3L, "POS-100"))
                .willReturn(List.of());
        given(productRepository.findAllByStoreIdAndIdIn(3L, java.util.Set.of(10L, 11L)))
                .willReturn(List.of(americano, latte));
        given(saleRepository.saveAllAndFlush(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        SaleTransactionResult result = saleService.createTransaction(jwt, 3L, request);

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().totalQuantity()).isEqualTo(3);
        assertThat(result.transaction().totalPaidAmount()).isEqualTo(13_500);
        assertThat(result.transaction().items()).extracting("productName")
                .containsExactly("아메리카노", "라테");
        assertThat(result.transaction().items()).extracting("productPrice")
                .containsExactly(4_500L, 5_000L);
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void returnsExistingTransactionForTheSamePayload() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        Sale existing = Sale.create(3L, "POS-100", 1, product, 2, 9_000, SOLD_AT);
        given(saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(3L, "POS-100"))
                .willReturn(List.of(existing));

        SaleTransactionResult result = saleService.createTransaction(
                jwt,
                3L,
                request(new SaleItemRequest(1, 10L, 2, 9_000))
        );

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().transactionId()).isEqualTo("POS-100");
        verify(saleRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsReusedTransactionIdWithDifferentPayload() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        Sale existing = Sale.create(3L, "POS-100", 1, product, 1, 4_500, SOLD_AT);
        given(saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(3L, "POS-100"))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request(new SaleItemRequest(1, 10L, 2, 9_000))
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.SALE_TRANSACTION_CONFLICT);
    }

    @Test
    void rejectsDuplicateLineNumbersBeforeSaving() {
        SaleTransactionCreateRequest request = request(
                new SaleItemRequest(1, 10L, 1, 4_500),
                new SaleItemRequest(1, 11L, 1, 5_000)
        );

        assertThatThrownBy(() -> saleService.createTransaction(jwt, 3L, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(saleRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsProductsThatDoNotBelongToTheStore() {
        given(saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(3L, "POS-100"))
                .willReturn(List.of());
        given(productRepository.findAllByStoreIdAndIdIn(3L, java.util.Set.of(10L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request(new SaleItemRequest(1, 10L, 1, 4_500))
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void cancellationIsIdempotentForEveryLine() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        List<Sale> existing = List.of(
                Sale.create(3L, "POS-100", 1, product, 1, 4_500, SOLD_AT),
                Sale.create(3L, "POS-100", 2, product, 2, 8_000, SOLD_AT)
        );
        given(saleRepository.findAllByStoreIdAndTransactionIdOrderByLineNoAsc(3L, "POS-100"))
                .willReturn(existing);

        var first = saleService.cancelTransaction(jwt, 3L, "POS-100");
        var second = saleService.cancelTransaction(jwt, 3L, "POS-100");

        assertThat(first.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(first.items()).allMatch(item -> item.status() == SaleStatus.CANCELLED);
        assertThat(second.cancelledAt()).isEqualTo(first.cancelledAt());
    }

    @Test
    void preservesSaleSnapshotAfterProductChanges() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        Sale sale = Sale.create(3L, "POS-100", 1, product, 1, 4_000, SOLD_AT);

        product.update("시즌 아메리카노", 5_000L);

        var response = com.onclick.domain.sale.dto.SaleTransactionResponse.from(List.of(sale));
        assertThat(response.items().getFirst().productName()).isEqualTo("아메리카노");
        assertThat(response.items().getFirst().productPrice()).isEqualTo(4_500);
        assertThat(response.items().getFirst().paidAmount()).isEqualTo(4_000);
    }

    private SaleTransactionCreateRequest request(SaleItemRequest... items) {
        return new SaleTransactionCreateRequest("POS-100", SOLD_AT, List.of(items));
    }

    private Product product(Long id, Long storeId, String name, long price) {
        Product product = Product.create(storeId, name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
