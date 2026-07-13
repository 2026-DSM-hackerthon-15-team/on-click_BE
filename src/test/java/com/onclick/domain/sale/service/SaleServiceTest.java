package com.onclick.domain.sale.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionResponse;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    private static final Instant SOLD_AT = Instant.parse("2026-07-13T03:15:00Z");
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @Mock
    private SaleTransactionRepository saleTransactionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private Jwt jwt;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        SaleTransactionExecutor transactionExecutor = new SaleTransactionExecutor(
                saleTransactionRepository,
                productRepository
        );
        saleService = new SaleService(
                transactionExecutor,
                storeAccessValidator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void savesOneTransactionHeaderWithAllItemsAndProductSnapshots() {
        Product americano = product(10L, 3L, "아메리카노", 4_500);
        Product latte = product(11L, 3L, "라테", 5_000);
        SaleTransactionCreateRequest request = request(
                "POS-100",
                new SaleItemRequest(1, 10L, 2, 9_000),
                new SaleItemRequest(2, 11L, 1, 4_500)
        );
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-100"))
                .willReturn(Optional.empty());
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L, 11L)))
                .willReturn(List.of(americano, latte));
        given(saleTransactionRepository.saveAndFlush(any(SaleTransaction.class)))
                .willAnswer(invocation -> persisted(invocation.getArgument(0), 100L));

        SaleTransactionResult result = saleService.createTransaction(jwt, 3L, request);

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().saleId()).isEqualTo(100L);
        assertThat(result.transaction().clientTransactionId()).isEqualTo("POS-100");
        assertThat(result.transaction().totalQuantity()).isEqualTo(3);
        assertThat(result.transaction().totalPaidAmount()).isEqualTo(13_500);
        assertThat(result.transaction().items()).extracting("productName")
                .containsExactly("아메리카노", "라테");
        assertThat(result.transaction().items()).extracting("productPrice")
                .containsExactly(4_500L, 5_000L);
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void createsANewServerSaleIdWhenClientTransactionIdIsMissing() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L)))
                .willReturn(List.of(product));
        given(saleTransactionRepository.saveAndFlush(any(SaleTransaction.class)))
                .willAnswer(invocation -> persisted(invocation.getArgument(0), 101L));

        SaleTransactionResult result = saleService.createTransaction(
                jwt,
                3L,
                request(null, new SaleItemRequest(1, 10L, 1, 4_500))
        );

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().saleId()).isEqualTo(101L);
        assertThat(result.transaction().clientTransactionId()).isNull();
        verify(saleTransactionRepository, never())
                .findByStoreIdAndClientTransactionId(any(), any());
    }

    @Test
    void returnsExistingTransactionForTheSameClientPayload() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction existing = transaction(77L, "POS-100", product, 2, 9_000);
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-100"))
                .willReturn(Optional.of(existing));

        SaleTransactionResult result = saleService.createTransaction(
                jwt,
                3L,
                request("POS-100", new SaleItemRequest(1, 10L, 2, 9_000))
        );

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().saleId()).isEqualTo(77L);
        verify(saleTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void treatsNanosecondDifferencesBeyondPostgresPrecisionAsTheSamePayload() {
        Instant nanosecondSoldAt = Instant.parse("2026-07-13T03:15:00.123456789Z");
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction existing = SaleTransaction.create(
                3L,
                "POS-NANO",
                Instant.parse("2026-07-13T03:15:00.123456Z")
        );
        existing.addItem(1, product, 1, 4_500);
        persisted(existing, 78L);
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-NANO"))
                .willReturn(Optional.of(existing));

        SaleTransactionResult result = saleService.createTransaction(
                jwt,
                3L,
                new SaleTransactionCreateRequest(
                        "POS-NANO",
                        nanosecondSoldAt,
                        List.of(new SaleItemRequest(1, 10L, 1, 4_500))
                )
        );

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().soldAt())
                .isEqualTo(Instant.parse("2026-07-13T03:15:00.123456Z"));
        verify(saleTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsConcurrentWinnerForTheSamePayloadAfterUniqueViolation() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction winner = transaction(79L, "POS-RACE", product, 1, 4_500);
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-RACE"))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L)))
                .willReturn(List.of(product));
        given(saleTransactionRepository.saveAndFlush(any(SaleTransaction.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("unique"));

        SaleTransactionResult result = saleService.createTransaction(
                jwt,
                3L,
                request("POS-RACE", new SaleItemRequest(1, 10L, 1, 4_500))
        );

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().saleId()).isEqualTo(79L);
    }

    @Test
    void rejectsConcurrentWinnerWithDifferentPayloadAfterUniqueViolation() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction winner = transaction(80L, "POS-RACE", product, 2, 9_000);
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-RACE"))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L)))
                .willReturn(List.of(product));
        given(saleTransactionRepository.saveAndFlush(any(SaleTransaction.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request("POS-RACE", new SaleItemRequest(1, 10L, 1, 4_500))
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.SALE_TRANSACTION_CONFLICT);
    }

    @Test
    void rejectsReusedClientTransactionIdWithDifferentPayload() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction existing = transaction(77L, "POS-100", product, 1, 4_500);
        given(saleTransactionRepository.findByStoreIdAndClientTransactionId(3L, "POS-100"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request("POS-100", new SaleItemRequest(1, 10L, 2, 9_000))
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.SALE_TRANSACTION_CONFLICT);
    }

    @Test
    void rejectsDuplicateLineNumbersBeforeSaving() {
        SaleTransactionCreateRequest request = request(
                null,
                new SaleItemRequest(1, 10L, 1, 4_500),
                new SaleItemRequest(1, 11L, 1, 5_000)
        );

        assertThatThrownBy(() -> saleService.createTransaction(jwt, 3L, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(saleTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsProductsThatDoNotBelongToTheStore() {
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request(null, new SaleItemRequest(1, 10L, 1, 4_500))
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void cancellationByServerSaleIdIsIdempotent() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction existing = transaction(77L, null, product, 2, 9_000);
        given(saleTransactionRepository.findByIdAndStoreId(77L, 3L))
                .willReturn(Optional.of(existing));
        doAnswer(invocation -> {
            existing.cancel(invocation.getArgument(2));
            return 1;
        }).when(saleTransactionRepository).cancelIfCompleted(77L, 3L, NOW);

        SaleTransactionResponse first = saleService.cancelTransaction(jwt, 3L, 77L);
        SaleTransactionResponse second = saleService.cancelTransaction(jwt, 3L, 77L);

        assertThat(first.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(first.cancelledAt()).isEqualTo(NOW);
        assertThat(second.cancelledAt()).isEqualTo(first.cancelledAt());
        assertThat(first.items()).allMatch(item -> item.paidAmount() == 9_000);
    }

    @Test
    void totalPaidAmountFailsFastOnOverflow() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction transaction = SaleTransaction.create(3L, null, SOLD_AT);
        transaction.addItem(1, product, 1, Long.MAX_VALUE);
        transaction.addItem(2, product, 1, 1L);

        assertThatThrownBy(transaction::totalPaidAmount)
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> SaleTransactionResponse.from(transaction))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsOverflowBeforePersistingTheTransaction() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        given(productRepository.findAllByStoreIdAndIdIn(3L, Set.of(10L)))
                .willReturn(List.of(product));

        assertThatThrownBy(() -> saleService.createTransaction(
                jwt,
                3L,
                request(
                        null,
                        new SaleItemRequest(1, 10L, 1, Long.MAX_VALUE),
                        new SaleItemRequest(2, 10L, 1, 1L)
                )
        )).isInstanceOf(ArithmeticException.class);

        verify(saleTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void totalQuantityFailsFastOnOverflow() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction transaction = SaleTransaction.create(3L, null, SOLD_AT);
        transaction.addItem(1, product, Integer.MAX_VALUE, 1L);
        transaction.addItem(2, product, 1, 1L);

        assertThatThrownBy(transaction::totalQuantity)
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> SaleTransactionResponse.from(transaction))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void preservesItemSnapshotAfterProductChanges() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction transaction = transaction(77L, null, product, 1, 4_000);

        product.update("시즌 아메리카노", 5_000L);

        SaleTransactionResponse response = SaleTransactionResponse.from(transaction);
        assertThat(response.items().getFirst().productName()).isEqualTo("아메리카노");
        assertThat(response.items().getFirst().productPrice()).isEqualTo(4_500);
        assertThat(response.items().getFirst().paidAmount()).isEqualTo(4_000);
    }

    private SaleTransactionCreateRequest request(
            String clientTransactionId,
            SaleItemRequest... items
    ) {
        return new SaleTransactionCreateRequest(clientTransactionId, SOLD_AT, List.of(items));
    }

    private SaleTransaction transaction(
            Long id,
            String clientTransactionId,
            Product product,
            int quantity,
            long paidAmount
    ) {
        SaleTransaction transaction = SaleTransaction.create(3L, clientTransactionId, SOLD_AT);
        transaction.addItem(1, product, quantity, paidAmount);
        return persisted(transaction, id);
    }

    private SaleTransaction persisted(SaleTransaction transaction, Long id) {
        ReflectionTestUtils.setField(transaction, "id", id);
        ReflectionTestUtils.setField(transaction, "createdAt", NOW);
        return transaction;
    }

    private Product product(Long id, Long storeId, String name, long price) {
        Product product = Product.create(storeId, name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
