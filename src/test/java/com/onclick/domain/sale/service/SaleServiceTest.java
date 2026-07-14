package com.onclick.domain.sale.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.sale.dto.SaleItemRequest;
import com.onclick.domain.sale.dto.SaleTransactionCreateRequest;
import com.onclick.domain.sale.dto.SaleTransactionPageResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime SOLD_AT = LocalDateTime.parse("2026-07-13T12:15:00");
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-07-13T13:00:00");

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
                saleTransactionRepository,
                storeAccessValidator,
                Clock.fixed(Instant.parse("2026-07-13T04:00:00Z"), BUSINESS_ZONE)
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
    void returnsTheRequestedPageInStableDatabaseOrder() {
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction first = transaction(
                101L,
                "POS-101",
                LocalDateTime.parse("2026-07-13T12:00:00"),
                product,
                1,
                4_500
        );
        SaleTransaction second = transaction(
                102L,
                "POS-102",
                LocalDateTime.parse("2026-07-13T12:00:00"),
                product,
                2,
                9_000
        );
        given(saleTransactionRepository.findPageIdsByStoreId(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(
                        List.of(102L, 101L),
                        PageRequest.of(1, 2),
                        5
                ));
        given(saleTransactionRepository.findAllWithItemsByStoreIdAndIdIn(
                3L,
                List.of(102L, 101L)
        )).willReturn(List.of(first, second));

        SaleTransactionPageResponse response = saleService.findTransactions(
                jwt,
                3L,
                1,
                2,
                "soldAt",
                "desc"
        );

        assertThat(response.content()).extracting(SaleTransactionResponse::saleId)
                .containsExactly(102L, 101L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.sortBy()).isEqualTo("soldAt");
        assertThat(response.sortDirection()).isEqualTo("desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(saleTransactionRepository).findPageIdsByStoreId(
                org.mockito.ArgumentMatchers.eq(3L),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort())
                .containsExactly(
                        Sort.Order.desc("soldAt"),
                        Sort.Order.desc("id")
                );
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void appliesWhitelistedAscendingSortAndRejectsInvalidPageQueries() {
        given(saleTransactionRepository.findPageIdsByStoreId(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0
                ));

        SaleTransactionPageResponse response = saleService.findTransactions(
                jwt,
                3L,
                0,
                20,
                "status",
                "ASC"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(saleTransactionRepository).findPageIdsByStoreId(
                org.mockito.ArgumentMatchers.eq(3L),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort())
                .containsExactly(
                        Sort.Order.asc("status"),
                        Sort.Order.asc("id")
                );
        assertThat(response.sortBy()).isEqualTo("status");
        assertThat(response.sortDirection()).isEqualTo("asc");

        assertThatThrownBy(() -> saleService.findTransactions(
                jwt,
                3L,
                0,
                20,
                "totalPaidAmount",
                "desc"
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> saleService.findTransactions(
                jwt,
                3L,
                0,
                101,
                "soldAt",
                "desc"
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> saleService.findTransactions(
                jwt,
                3L,
                0,
                20,
                "soldAt",
                "newest"
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void skipsTheItemFetchForAnEmptyPage() {
        given(saleTransactionRepository.findPageIdsByStoreId(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(2, 20),
                        3
                ));

        SaleTransactionPageResponse response = saleService.findTransactions(
                jwt,
                3L,
                2,
                20,
                "soldAt",
                "desc"
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        verify(saleTransactionRepository, never())
                .findAllWithItemsByStoreIdAndIdIn(any(), any());
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
        LocalDateTime nanosecondSoldAt = LocalDateTime.parse("2026-07-13T12:15:00.123456789");
        Product product = product(10L, 3L, "아메리카노", 4_500);
        SaleTransaction existing = SaleTransaction.create(
                3L,
                "POS-NANO",
                LocalDateTime.parse("2026-07-13T12:15:00.123456")
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
                .isEqualTo(LocalDateTime.parse("2026-07-13T12:15:00.123456"));
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

    private SaleTransaction transaction(
            Long id,
            String clientTransactionId,
            LocalDateTime soldAt,
            Product product,
            int quantity,
            long paidAmount
    ) {
        SaleTransaction transaction = SaleTransaction.create(3L, clientTransactionId, soldAt);
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
