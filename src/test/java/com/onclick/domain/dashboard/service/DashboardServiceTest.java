package com.onclick.domain.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long STORE_ID = 3L;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 13);
    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-07-12T15:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-07-13T15:00:00Z");

    @Mock
    private SaleTransactionRepository saleTransactionRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private AiClient aiClient;

    @Mock
    private Jwt jwt;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        dashboardService = new DashboardService(
                saleTransactionRepository,
                storeAccessValidator,
                aiClient,
                clock
        );
        given(storeAccessValidator.validate(jwt, STORE_ID))
                .willReturn(new Store(
                        new User("dashboard-owner", "password-hash"),
                        "강남점",
                        STORE_ZONE.getId()
                ));
    }

    @Test
    void summaryCountsEachCompletedTransactionAsOneOrderAndOneVisitor() {
        List<SaleTransaction> transactions = List.of(
                completedTransaction("TX-1", atHour(9, 10), item(1, 2, 5_000), item(2, 1, 3_000)),
                completedTransaction("TX-2", atHour(10, 5), item(1, 1, 7_000)),
                cancelledTransaction("TX-3", atHour(11, 0), item(1, 1, 99_000))
        );
        givenTransactionsForToday(transactions);

        var response = dashboardService.getSummary(jwt, STORE_ID);

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(response.totalSalesAmount()).isEqualTo(15_000);
        assertThat(response.orderCount()).isEqualTo(2);
        assertThat(response.totalVisitors()).isEqualTo(2);
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void hourlySalesFillsTwentyFourBucketsFromTransactionItemsAndExcludesCancelledTransactions() {
        List<SaleTransaction> transactions = List.of(
                completedTransaction("TX-1", atHour(9, 10), item(1, 2, 5_000), item(2, 1, 3_000)),
                completedTransaction("TX-2", atHour(10, 5), item(1, 1, 7_000)),
                completedTransaction("TX-3", atHour(10, 40), item(1, 2, 2_000)),
                cancelledTransaction("TX-4", atHour(10, 50), item(1, 5, 99_000))
        );
        givenTransactionsForToday(transactions);

        var response = dashboardService.getHourlySales(jwt, STORE_ID);

        assertThat(response.hourly()).hasSize(24);
        assertThat(response.hourly()).extracting(item -> item.hour())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 24).boxed().toList());
        assertThat(response.hourly().get(8).salesAmount()).isZero();
        assertThat(response.hourly().get(9).salesAmount()).isEqualTo(8_000);
        assertThat(response.hourly().get(9).quantity()).isEqualTo(3);
        assertThat(response.hourly().get(9).orderCount()).isEqualTo(1);
        assertThat(response.hourly().get(10).salesAmount()).isEqualTo(9_000);
        assertThat(response.hourly().get(10).quantity()).isEqualTo(3);
        assertThat(response.hourly().get(10).orderCount()).isEqualTo(2);
        assertThat(response.totalSalesAmount()).isEqualTo(17_000);
        assertThat(response.totalQuantity()).isEqualTo(6);
        assertThat(response.orderCount()).isEqualTo(3);
    }

    @Test
    void hourlyVisitorsCountsCompletedTransactionsInsteadOfItems() {
        List<SaleTransaction> transactions = List.of(
                completedTransaction("TX-1", atHour(7, 5), item(1, 1, 1_000), item(2, 3, 3_000)),
                completedTransaction("TX-2", atHour(18, 20), item(1, 1, 2_000)),
                cancelledTransaction("TX-3", atHour(7, 40), item(1, 1, 5_000))
        );
        givenTransactionsForToday(transactions);

        var response = dashboardService.getHourlyVisitors(jwt, STORE_ID);

        assertThat(response.hourly()).hasSize(24);
        assertThat(response.hourly()).extracting(item -> item.hour())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 24).boxed().toList());
        assertThat(response.hourly().get(6).visitorCount()).isZero();
        assertThat(response.hourly().get(7).visitorCount()).isEqualTo(1);
        assertThat(response.hourly().get(18).visitorCount()).isEqualTo(1);
        assertThat(response.hourly().get(19).visitorCount()).isZero();
        assertThat(response.totalVisitors()).isEqualTo(2);
    }

    @Test
    void forecastsKeepPublicContractAndUseCompletedTransactionSales() {
        Instant generatedAt = Instant.parse("2026-07-13T06:00:30Z");
        givenTransactionsForToday(List.of(
                completedTransaction("TX-1", atHour(9, 10), item(1, 2, 8_000)),
                completedTransaction("TX-2", atHour(10, 5), item(1, 1, 2_000)),
                cancelledTransaction("TX-3", atHour(11, 0), item(1, 1, 90_000))
        ));
        ClosingSalesForecastRequest closingRequest = new ClosingSalesForecastRequest(STORE_ID, BUSINESS_DATE);
        TomorrowVisitorsForecastRequest visitorsRequest = new TomorrowVisitorsForecastRequest(
                STORE_ID,
                BUSINESS_DATE.plusDays(1)
        );
        given(aiClient.forecastClosingSales(closingRequest))
                .willReturn(new ClosingSalesForecastResult(25_000, generatedAt));
        given(aiClient.forecastTomorrowVisitors(visitorsRequest))
                .willReturn(new TomorrowVisitorsForecastResult(120, generatedAt));

        var closing = dashboardService.getClosingSalesForecast(jwt, STORE_ID);
        var visitors = dashboardService.getTomorrowVisitorsForecast(jwt, STORE_ID);

        assertThat(closing.observedSalesAmount()).isEqualTo(10_000);
        assertThat(closing.forecastClosingSalesAmount()).isEqualTo(25_000);
        assertThat(closing.generatedAt()).isEqualTo(generatedAt);
        assertThat(visitors.targetDate()).isEqualTo(BUSINESS_DATE.plusDays(1));
        assertThat(visitors.expectedVisitors()).isEqualTo(120);
        assertThat(visitors.generatedAt()).isEqualTo(generatedAt);
        verify(aiClient).forecastClosingSales(closingRequest);
        verify(aiClient).forecastTomorrowVisitors(visitorsRequest);
    }

    private void givenTransactionsForToday(List<SaleTransaction> transactions) {
        given(saleTransactionRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        STORE_ID,
                        DAY_START,
                        DAY_END
                ))
                .willReturn(transactions);
    }

    private SaleTransaction completedTransaction(
            String clientTransactionId,
            Instant soldAt,
            Item... items
    ) {
        SaleTransaction transaction = SaleTransaction.create(STORE_ID, clientTransactionId, soldAt);
        for (Item item : items) {
            transaction.addItem(item.lineNo(), product(), item.quantity(), item.paidAmount());
        }
        return transaction;
    }

    private SaleTransaction cancelledTransaction(
            String clientTransactionId,
            Instant soldAt,
            Item... items
    ) {
        SaleTransaction transaction = completedTransaction(clientTransactionId, soldAt, items);
        transaction.cancel(NOW);
        return transaction;
    }

    private Item item(int lineNo, int quantity, long paidAmount) {
        return new Item(lineNo, quantity, paidAmount);
    }

    private Product product() {
        Product product = Product.create(STORE_ID, "테스트 상품", 4_500);
        ReflectionTestUtils.setField(product, "id", 10L);
        return product;
    }

    private Instant atHour(int hour, int minute) {
        return BUSINESS_DATE.atTime(hour, minute).atZone(STORE_ZONE).toInstant();
    }

    private record Item(int lineNo, int quantity, long paidAmount) {
    }
}
