package com.onclick.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.repository.SaleRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.domain.visitor.entity.HourlyVisitorCount;
import com.onclick.domain.visitor.repository.HourlyVisitorCountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long STORE_ID = 3L;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 13);
    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-07-12T15:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-07-13T15:00:00Z");

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private HourlyVisitorCountRepository visitorCountRepository;

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
                saleRepository,
                visitorCountRepository,
                storeAccessValidator,
                aiClient,
                clock
        );

        Store store = new Store("강남점", STORE_ZONE.getId());
        UserStoreMembership membership = new UserStoreMembership(null, store, StoreRole.OWNER);
        given(storeAccessValidator.validate(jwt, STORE_ID)).willReturn(membership);
    }

    @Test
    void summaryUsesOnlyCompletedSalesDistinctTransactionsAndVisitorTotal() {
        List<Sale> sales = List.of(
                completedSale("TX-1", 1, 2, 5_000, atHour(9, 10)),
                completedSale("TX-1", 2, 1, 3_000, atHour(9, 20)),
                completedSale("TX-2", 1, 1, 7_000, atHour(10, 5)),
                cancelledSale("TX-3", 1, 1, 99_000, atHour(11, 0))
        );
        givenSalesForToday(sales);
        given(visitorCountRepository.findAllByStoreIdAndBusinessDateOrderByHourAsc(
                STORE_ID,
                BUSINESS_DATE
        )).willReturn(List.of(visitorCount(9, 10), visitorCount(10, 15)));

        var response = dashboardService.getSummary(jwt, STORE_ID);

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(response.totalSalesAmount()).isEqualTo(15_000);
        assertThat(response.orderCount()).isEqualTo(2);
        assertThat(response.totalVisitors()).isEqualTo(25);
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void hourlySalesFillsTwentyFourBucketsAndExcludesCancelledSales() {
        List<Sale> sales = List.of(
                completedSale("TX-1", 1, 2, 5_000, atHour(9, 10)),
                completedSale("TX-1", 2, 1, 3_000, atHour(9, 20)),
                completedSale("TX-2", 1, 1, 7_000, atHour(10, 5)),
                completedSale("TX-3", 1, 2, 2_000, atHour(10, 40)),
                cancelledSale("TX-4", 1, 5, 99_000, atHour(10, 50))
        );
        givenSalesForToday(sales);

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
    void hourlyVisitorsFillsMissingHoursWithZero() {
        given(visitorCountRepository.findAllByStoreIdAndBusinessDateOrderByHourAsc(
                STORE_ID,
                BUSINESS_DATE
        )).willReturn(List.of(visitorCount(7, 4), visitorCount(18, 9)));

        var response = dashboardService.getHourlyVisitors(jwt, STORE_ID);

        assertThat(response.hourly()).hasSize(24);
        assertThat(response.hourly()).extracting(item -> item.hour())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 24).boxed().toList());
        assertThat(response.hourly().get(6).visitorCount()).isZero();
        assertThat(response.hourly().get(7).visitorCount()).isEqualTo(4);
        assertThat(response.hourly().get(18).visitorCount()).isEqualTo(9);
        assertThat(response.hourly().get(19).visitorCount()).isZero();
        assertThat(response.totalVisitors()).isEqualTo(13);
    }

    @Test
    void forecastsMapAiClientResultsAndObservedSales() {
        Instant generatedAt = Instant.parse("2026-07-13T06:00:30Z");
        givenSalesForToday(List.of(
                completedSale("TX-1", 1, 2, 8_000, atHour(9, 10)),
                completedSale("TX-2", 1, 1, 2_000, atHour(10, 5)),
                cancelledSale("TX-3", 1, 1, 90_000, atHour(11, 0))
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

    private void givenSalesForToday(List<Sale> sales) {
        given(saleRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        STORE_ID,
                        DAY_START,
                        DAY_END
                ))
                .willReturn(sales);
    }

    private Sale completedSale(
            String transactionId,
            int lineNo,
            int quantity,
            long paidAmount,
            Instant soldAt
    ) {
        return Sale.create(
                STORE_ID,
                transactionId,
                lineNo,
                product(),
                quantity,
                paidAmount,
                soldAt
        );
    }

    private Sale cancelledSale(
            String transactionId,
            int lineNo,
            int quantity,
            long paidAmount,
            Instant soldAt
    ) {
        Sale sale = completedSale(transactionId, lineNo, quantity, paidAmount, soldAt);
        sale.cancel(NOW);
        return sale;
    }

    private Product product() {
        Product product = Product.create(STORE_ID, "테스트 상품", 4_500);
        ReflectionTestUtils.setField(product, "id", 10L);
        return product;
    }

    private HourlyVisitorCount visitorCount(int hour, long count) {
        return new HourlyVisitorCount(STORE_ID, BUSINESS_DATE, hour, count, NOW);
    }

    private Instant atHour(int hour, int minute) {
        return BUSINESS_DATE.atTime(hour, minute).atZone(STORE_ZONE).toInstant();
    }
}
