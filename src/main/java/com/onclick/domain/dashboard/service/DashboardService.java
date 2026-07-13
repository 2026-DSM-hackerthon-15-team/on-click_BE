package com.onclick.domain.dashboard.service;

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
import com.onclick.domain.sale.entity.Sale;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.repository.SaleRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.domain.visitor.entity.HourlyVisitorCount;
import com.onclick.domain.visitor.repository.HourlyVisitorCountRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int HOURS_PER_DAY = 24;
    private static final String CURRENCY = "KRW";

    private final SaleRepository saleRepository;
    private final HourlyVisitorCountRepository visitorCountRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final AiClient aiClient;
    private final Clock clock;

    public DashboardService(
            SaleRepository saleRepository,
            HourlyVisitorCountRepository visitorCountRepository,
            StoreAccessValidator storeAccessValidator,
            AiClient aiClient,
            Clock clock
    ) {
        this.saleRepository = saleRepository;
        this.visitorCountRepository = visitorCountRepository;
        this.storeAccessValidator = storeAccessValidator;
        this.aiClient = aiClient;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<Sale> sales = completedSales(storeId, businessDate, store.zoneId());
        List<HourlyVisitorCount> visitors = visitorCountRepository
                .findAllByStoreIdAndBusinessDateOrderByHourAsc(storeId, businessDate);

        return new DashboardSummaryResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                CURRENCY,
                totalSales(sales),
                sales.stream().map(Sale::getTransactionId).distinct().count(),
                visitors.stream().mapToLong(HourlyVisitorCount::getVisitorCount).sum(),
                clock.instant()
        );
    }

    public HourlySalesResponse getHourlySales(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<Sale> sales = completedSales(storeId, businessDate, store.zoneId());

        long[] amounts = new long[HOURS_PER_DAY];
        long[] quantities = new long[HOURS_PER_DAY];
        List<Set<String>> transactions = transactionSets();
        for (Sale sale : sales) {
            int hour = sale.getSoldAt().atZone(store.zoneId()).getHour();
            amounts[hour] = Math.addExact(amounts[hour], sale.getPaidAmount());
            quantities[hour] = Math.addExact(quantities[hour], sale.getQuantity());
            transactions.get(hour).add(sale.getTransactionId());
        }

        List<HourlySalesItem> hourly = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourly.add(new HourlySalesItem(
                    hour,
                    amounts[hour],
                    quantities[hour],
                    transactions.get(hour).size()
            ));
        }

        return new HourlySalesResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                CURRENCY,
                sum(amounts),
                sum(quantities),
                sales.stream().map(Sale::getTransactionId).distinct().count(),
                hourly
        );
    }

    public HourlyVisitorsResponse getHourlyVisitors(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        List<HourlyVisitorCount> counts = visitorCountRepository
                .findAllByStoreIdAndBusinessDateOrderByHourAsc(storeId, businessDate);
        Map<Integer, Long> countsByHour = new HashMap<>();
        for (HourlyVisitorCount count : counts) {
            countsByHour.put(count.getHour(), count.getVisitorCount());
        }

        List<HourlyVisitorItem> hourly = new ArrayList<>(HOURS_PER_DAY);
        long totalVisitors = 0;
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            long visitorCount = countsByHour.getOrDefault(hour, 0L);
            totalVisitors = Math.addExact(totalVisitors, visitorCount);
            hourly.add(new HourlyVisitorItem(hour, visitorCount));
        }

        return new HourlyVisitorsResponse(
                storeId,
                businessDate,
                store.getTimeZone(),
                totalVisitors,
                hourly
        );
    }

    public ClosingSalesForecastResponse getClosingSalesForecast(Jwt jwt, Long storeId) {
        Store store = accessibleStore(jwt, storeId);
        LocalDate businessDate = today(store);
        long observedSales = totalSales(completedSales(storeId, businessDate, store.zoneId()));
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
        return storeAccessValidator.validate(jwt, storeId).getStore();
    }

    private LocalDate today(Store store) {
        return LocalDate.now(clock.withZone(store.zoneId()));
    }

    private List<Sale> completedSales(Long storeId, LocalDate date, ZoneId zoneId) {
        Instant from = date.atStartOfDay(zoneId).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return saleRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(storeId, from, to)
                .stream()
                .filter(sale -> sale.getStatus() == SaleStatus.COMPLETED)
                .toList();
    }

    private long totalSales(List<Sale> sales) {
        long total = 0;
        for (Sale sale : sales) {
            total = Math.addExact(total, sale.getPaidAmount());
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

    private List<Set<String>> transactionSets() {
        List<Set<String>> result = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            result.add(new HashSet<>());
        }
        return result;
    }
}
