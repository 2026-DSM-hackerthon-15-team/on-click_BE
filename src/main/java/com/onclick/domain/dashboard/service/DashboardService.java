package com.onclick.domain.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import com.onclick.domain.dashboard.dto.ClosingSalesForecastResponse;
import com.onclick.domain.dashboard.dto.DashboardSummaryResponse;
import com.onclick.domain.dashboard.dto.HourlySalesItem;
import com.onclick.domain.dashboard.dto.HourlySalesResponse;
import com.onclick.domain.dashboard.dto.HourlyVisitorItem;
import com.onclick.domain.dashboard.dto.HourlyVisitorsResponse;
import com.onclick.domain.dashboard.dto.TomorrowVisitorsForecastResponse;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int HOURS_PER_DAY = 24;
    private static final String CURRENCY = "KRW";

    private final SaleTransactionRepository saleTransactionRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final AiClient aiClient;
    private final Clock clock;

    public DashboardService(
            SaleTransactionRepository saleTransactionRepository,
            StoreAccessValidator storeAccessValidator,
            AiClient aiClient,
            Clock clock
    ) {
        this.saleTransactionRepository = saleTransactionRepository;
        this.storeAccessValidator = storeAccessValidator;
        this.aiClient = aiClient;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<SaleTransaction> transactions = completedTransactions(
                storeId,
                businessDate,
                store.zoneId()
        );

        return new DashboardSummaryResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                CURRENCY,
                totalSales(transactions),
                transactions.size(),
                transactions.size(),
                clock.instant()
        );
    }

    public HourlySalesResponse getHourlySales(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<SaleTransaction> transactions = completedTransactions(
                storeId,
                businessDate,
                store.zoneId()
        );

        long[] amounts = new long[HOURS_PER_DAY];
        long[] quantities = new long[HOURS_PER_DAY];
        long[] orderCounts = new long[HOURS_PER_DAY];
        for (SaleTransaction transaction : transactions) {
            int hour = transaction.getSoldAt().atZone(store.zoneId()).getHour();
            amounts[hour] = Math.addExact(amounts[hour], transaction.totalPaidAmount());
            quantities[hour] = Math.addExact(quantities[hour], transaction.totalQuantity());
            orderCounts[hour] = Math.addExact(orderCounts[hour], 1L);
        }

        List<HourlySalesItem> hourly = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourly.add(new HourlySalesItem(
                    hour,
                    amounts[hour],
                    quantities[hour],
                    orderCounts[hour]
            ));
        }

        return new HourlySalesResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                CURRENCY,
                sum(amounts),
                sum(quantities),
                transactions.size(),
                hourly
        );
    }

    public HourlyVisitorsResponse getHourlyVisitors(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<SaleTransaction> transactions = completedTransactions(
                storeId,
                businessDate,
                store.zoneId()
        );

        long[] visitors = new long[HOURS_PER_DAY];
        for (SaleTransaction transaction : transactions) {
            int hour = transaction.getSoldAt().atZone(store.zoneId()).getHour();
            visitors[hour] = Math.addExact(visitors[hour], 1L);
        }

        List<HourlyVisitorItem> hourly = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourly.add(new HourlyVisitorItem(hour, visitors[hour]));
        }

        return new HourlyVisitorsResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                transactions.size(),
                hourly
        );
    }

    public ClosingSalesForecastResponse getClosingSalesForecast(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        long observedSales = totalSales(completedTransactions(storeId, businessDate, store.zoneId()));
        ClosingSalesForecastResult result = aiClient.forecastClosingSales(
                new ClosingSalesForecastRequest(storeId, businessDate)
        );
        return new ClosingSalesForecastResponse(
                storeId,
                businessDate,
                CURRENCY,
                observedSales,
                result.expectedClosingSales(),
                result.generatedAt()
        );
    }

    public TomorrowVisitorsForecastResponse getTomorrowVisitorsForecast(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate targetDate = today(store).plusDays(1);
        TomorrowVisitorsForecastResult result = aiClient.forecastTomorrowVisitors(
                new TomorrowVisitorsForecastRequest(storeId, targetDate)
        );
        return new TomorrowVisitorsForecastResponse(
                storeId,
                targetDate,
                result.expectedVisitors(),
                result.generatedAt()
        );
    }

    private Store accessibleStore(Jwt jwt, Long storeId) {
        return storeAccessValidator.validate(jwt, storeId);
    }

    private LocalDate today(Store store) {
        return LocalDate.now(clock.withZone(store.zoneId()));
    }

    private List<SaleTransaction> completedTransactions(
            Long storeId,
            LocalDate date,
            ZoneId zoneId
    ) {
        Instant from = date.atStartOfDay(zoneId).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return saleTransactionRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        storeId,
                        from,
                        to
                )
                .stream()
                .filter(transaction -> transaction.getStatus() == SaleStatus.COMPLETED)
                .toList();
    }

    private long totalSales(List<SaleTransaction> transactions) {
        long total = 0;
        for (SaleTransaction transaction : transactions) {
            total = Math.addExact(total, transaction.totalPaidAmount());
        }
        return total;
    }

    private long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total = Math.addExact(total, value);
        }
        return total;
    }
}
