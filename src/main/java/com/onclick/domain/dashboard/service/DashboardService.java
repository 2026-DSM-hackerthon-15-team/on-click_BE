package com.onclick.domain.dashboard.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.SaleTransactionInput;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import com.onclick.common.time.KoreanTime;
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
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int HOURS_PER_DAY = 24;
    private static final String CURRENCY = "KRW";

    private final SaleTransactionRepository saleTransactionRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final AiClient aiClient;
    private final Clock clock;

    public DashboardSummaryResponse getSummary(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        LocalDate businessDate = today();
        List<SaleTransaction> transactions = completedTransactions(storeId, businessDate);

        return new DashboardSummaryResponse(
                storeId,
                businessDate,
                CURRENCY,
                totalSales(transactions),
                transactions.size(),
                transactions.size(),
                KoreanTime.now(clock)
        );
    }

    public HourlySalesResponse getHourlySales(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        LocalDate businessDate = today();
        List<SaleTransaction> transactions = completedTransactions(storeId, businessDate);

        long[] amounts = new long[HOURS_PER_DAY];
        long[] quantities = new long[HOURS_PER_DAY];
        long[] orderCounts = new long[HOURS_PER_DAY];
        for (SaleTransaction transaction : transactions) {
            int hour = transaction.getSoldAt().getHour();
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
                CURRENCY,
                sum(amounts),
                sum(quantities),
                transactions.size(),
                hourly
        );
    }

    public HourlyVisitorsResponse getHourlyVisitors(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        LocalDate businessDate = today();
        List<SaleTransaction> transactions = completedTransactions(storeId, businessDate);

        long[] visitors = new long[HOURS_PER_DAY];
        for (SaleTransaction transaction : transactions) {
            int hour = transaction.getSoldAt().getHour();
            visitors[hour] = Math.addExact(visitors[hour], 1L);
        }

        List<HourlyVisitorItem> hourly = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourly.add(new HourlyVisitorItem(hour, visitors[hour]));
        }

        return new HourlyVisitorsResponse(
                storeId,
                businessDate,
                transactions.size(),
                hourly
        );
    }

    public ClosingSalesForecastResponse getClosingSalesForecast(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        LocalDateTime asOf = now();
        LocalDate businessDate = asOf.toLocalDate();
        List<SaleTransaction> transactions = saleTransactionRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanEqualOrderBySoldAtAsc(
                        storeId,
                        businessDate.atStartOfDay(),
                        asOf
                );
        requirePosData(transactions);
        long observedSales = totalSales(transactions.stream()
                .filter(transaction -> transaction.getStatus() == SaleStatus.COMPLETED)
                .toList());
        ClosingSalesForecastResult result = aiClient.forecastClosingSales(
                new ClosingSalesForecastRequest(storeId, asOf, toAiSalesData(transactions))
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
        storeAccessValidator.validate(jwt, storeId);
        LocalDate baseDate = today();
        LocalDate targetDate = baseDate.plusDays(1);
        List<SaleTransaction> transactions = saleTransactionRepository
                .findAllByStoreIdAndSoldAtLessThanOrderBySoldAtAsc(
                        storeId,
                        targetDate.atStartOfDay()
                );
        requirePosData(transactions);
        TomorrowVisitorsForecastResult result = aiClient.forecastTomorrowVisitors(
                new TomorrowVisitorsForecastRequest(storeId, baseDate, toAiSalesData(transactions))
        );
        return new TomorrowVisitorsForecastResponse(
                storeId,
                targetDate,
                result.expectedVisitors(),
                result.generatedAt()
        );
    }

    private LocalDate today() {
        return now().toLocalDate();
    }

    private LocalDateTime now() {
        return KoreanTime.now(clock);
    }

    private List<SaleTransaction> completedTransactions(Long storeId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return saleTransactionRepository
                .findAllByStoreIdAndStatusAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        storeId,
                        SaleStatus.COMPLETED,
                        from,
                        to
                );
    }

    private long totalSales(List<SaleTransaction> transactions) {
        long total = 0;
        for (SaleTransaction transaction : transactions) {
            total = Math.addExact(total, transaction.totalPaidAmount());
        }
        return total;
    }

    private List<SaleTransactionInput> toAiSalesData(List<SaleTransaction> transactions) {
        return transactions.stream()
                .map(transaction -> new SaleTransactionInput(
                        transaction.getSoldAt(),
                        transaction.totalPaidAmount(),
                        SaleTransactionInput.Status.valueOf(transaction.getStatus().name())
                ))
                .toList();
    }

    private void requirePosData(List<SaleTransaction> transactions) {
        if (transactions.isEmpty()) {
            throw new ApiException(ErrorCode.POS_DATA_NOT_FOUND);
        }
    }

    private long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total = Math.addExact(total, value);
        }
        return total;
    }
}
