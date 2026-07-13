package com.onclick.domain.consulting.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingHourlySales;
import com.onclick.common.ai.dto.ConsultingProductSales;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.sale.entity.SaleItem;
import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;
import com.onclick.domain.sale.repository.SaleTransactionRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ConsultingRequestFactory {

    private static final int HOURS_PER_DAY = 24;

    private final StoreRepository storeRepository;
    private final SaleTransactionRepository saleTransactionRepository;

    public ConsultingRequestFactory(
            StoreRepository storeRepository,
            SaleTransactionRepository saleTransactionRepository
    ) {
        this.storeRepository = storeRepository;
        this.saleTransactionRepository = saleTransactionRepository;
    }

    public ConsultingGenerationRequest create(ConsultingJobClaim job) {
        Store store = storeRepository.findById(job.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        Instant from = job.targetDate().atStartOfDay(store.zoneId()).toInstant();
        Instant to = job.targetDate().plusDays(1).atStartOfDay(store.zoneId()).toInstant();
        List<SaleTransaction> transactions = saleTransactionRepository
                .findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        store.getId(),
                        from,
                        to
                )
                .stream()
                .filter(transaction -> transaction.getStatus() == SaleStatus.COMPLETED)
                .toList();

        long totalSales = 0;
        long totalQuantity = 0;
        long[] hourlySales = new long[HOURS_PER_DAY];
        long[] hourlyOrders = new long[HOURS_PER_DAY];
        Map<ProductKey, ProductAggregate> productAggregates = new LinkedHashMap<>();

        for (SaleTransaction transaction : transactions) {
            long transactionSales = transaction.totalPaidAmount();
            totalSales = Math.addExact(totalSales, transactionSales);
            totalQuantity = Math.addExact(totalQuantity, transaction.totalQuantity());

            int hour = transaction.getSoldAt().atZone(store.zoneId()).getHour();
            hourlySales[hour] = Math.addExact(hourlySales[hour], transactionSales);
            hourlyOrders[hour] = Math.addExact(hourlyOrders[hour], 1L);

            for (SaleItem item : transaction.getItems()) {
                Product product = item.getProduct();
                ProductKey key = new ProductKey(product.getId(), item.getProductNameSnapshot());
                productAggregates.computeIfAbsent(key, ignored -> new ProductAggregate())
                        .add(item.getQuantity(), item.getPaidAmount());
            }
        }

        List<ConsultingProductSales> products = productAggregates.entrySet().stream()
                .map(entry -> new ConsultingProductSales(
                        entry.getKey().productId(),
                        entry.getKey().productName(),
                        entry.getValue().quantity,
                        entry.getValue().salesAmount
                ))
                .sorted(Comparator.comparingLong(ConsultingProductSales::salesAmount)
                        .reversed()
                        .thenComparing(ConsultingProductSales::productName)
                        .thenComparing(ConsultingProductSales::productId))
                .toList();

        List<ConsultingHourlySales> hourly = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourly.add(new ConsultingHourlySales(hour, hourlySales[hour], hourlyOrders[hour]));
        }

        return new ConsultingGenerationRequest(
                job.consultingId(),
                store.getId(),
                store.getName(),
                job.targetDate(),
                store.getTimeZone(),
                totalSales,
                transactions.size(),
                transactions.size(),
                totalQuantity,
                products,
                hourly
        );
    }

    private record ProductKey(Long productId, String productName) {
    }

    private static final class ProductAggregate {

        private long quantity;
        private long salesAmount;

        private void add(long quantity, long salesAmount) {
            this.quantity = Math.addExact(this.quantity, quantity);
            this.salesAmount = Math.addExact(this.salesAmount, salesAmount);
        }
    }
}
