package com.onclick.domain.consulting.service;

import java.time.LocalDateTime;
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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsultingRequestFactory {

    private static final int HOURS_PER_DAY = 24;

    private final StoreRepository storeRepository;
    private final SaleTransactionRepository saleTransactionRepository;

    public ConsultingGenerationRequest create(ConsultingJobClaim job) {
        Store store = storeRepository.findById(job.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        LocalDateTime from = job.targetDate().atStartOfDay();
        LocalDateTime to = job.targetDate().plusDays(1).atStartOfDay();
        List<SaleTransaction> transactions = saleTransactionRepository
                .findAllByStoreIdAndStatusAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
                        store.getId(),
                        SaleStatus.COMPLETED,
                        from,
                        to
                );

        long totalSales = 0;
        long totalQuantity = 0;
        long[] hourlySales = new long[HOURS_PER_DAY];
        long[] hourlyOrders = new long[HOURS_PER_DAY];
        Map<ProductKey, ProductAggregate> productAggregates = new LinkedHashMap<>();

        for (SaleTransaction transaction : transactions) {
            long transactionSales = transaction.totalPaidAmount();
            totalSales = Math.addExact(totalSales, transactionSales);
            totalQuantity = Math.addExact(totalQuantity, transaction.totalQuantity());

            int hour = transaction.getSoldAt().getHour();
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
