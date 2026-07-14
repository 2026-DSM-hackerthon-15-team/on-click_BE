package com.onclick.domain.consulting.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.onclick.domain.consulting.TestFieldSetter;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConsultingRequestFactoryTest {

    private static final Long STORE_ID = 3L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private SaleTransactionRepository saleTransactionRepository;

    private ConsultingRequestFactory requestFactory;

    @BeforeEach
    void setUp() {
        requestFactory = new ConsultingRequestFactory(
                storeRepository,
                saleTransactionRepository
        );
    }

    @Test
    void aggregatesOnlyCompletedTransactionsIntoProductAndHourlyInputs() {
        Store store = new Store(
                new User("owner", "hash"),
                "강남점"
        );
        TestFieldSetter.setField(store, "id", STORE_ID);
        Product coffee = product(10L, "커피");
        Product cake = product(11L, "케이크");
        SaleTransaction first = transaction(
                "TX-1",
                LocalDateTime.of(2026, 7, 13, 9, 10),
                coffee,
                2,
                8_000
        );
        first.addItem(2, cake, 1, 5_000);
        SaleTransaction second = transaction(
                "TX-2",
                LocalDateTime.of(2026, 7, 13, 10, 20),
                coffee,
                1,
                4_000
        );
        given(storeRepository.findById(STORE_ID)).willReturn(java.util.Optional.of(store));
        given(saleTransactionRepository
                .findAllByStoreIdAndStatusAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        STORE_ID,
                        SaleStatus.COMPLETED,
                        LocalDateTime.of(2026, 7, 13, 0, 0),
                        LocalDateTime.of(2026, 7, 14, 0, 0)
                ))
                .willReturn(List.of(first, second));

        var request = requestFactory.create(new ConsultingJobClaim(20L, STORE_ID, TARGET_DATE, 1));

        assertThat(request.totalSalesAmount()).isEqualTo(17_000);
        assertThat(request.orderCount()).isEqualTo(2);
        assertThat(request.totalVisitors()).isEqualTo(2);
        assertThat(request.totalQuantity()).isEqualTo(4);
        assertThat(request.products()).extracting(item -> item.productName())
                .containsExactly("커피", "케이크");
        assertThat(request.products().getFirst().quantity()).isEqualTo(3);
        assertThat(request.products().getFirst().salesAmount()).isEqualTo(12_000);
        assertThat(request.hourlySales()).hasSize(24);
        assertThat(request.hourlySales().get(9).salesAmount()).isEqualTo(13_000);
        assertThat(request.hourlySales().get(9).orderCount()).isEqualTo(1);
        assertThat(request.hourlySales().get(10).salesAmount()).isEqualTo(4_000);
        assertThat(request.hourlySales().get(10).orderCount()).isEqualTo(1);
    }

    private Product product(Long id, String name) {
        Product product = Product.create(STORE_ID, name, 4_000);
        TestFieldSetter.setField(product, "id", id);
        return product;
    }

    private SaleTransaction transaction(
            String clientId,
            LocalDateTime soldAt,
            Product product,
            int quantity,
            long amount
    ) {
        SaleTransaction transaction = SaleTransaction.create(STORE_ID, clientId, soldAt);
        transaction.addItem(1, product, quantity, amount);
        return transaction;
    }
}
