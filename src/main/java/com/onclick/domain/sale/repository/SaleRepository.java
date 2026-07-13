package com.onclick.domain.sale.repository;

import java.time.Instant;
import java.util.List;

import com.onclick.domain.sale.entity.Sale;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findAllByStoreIdAndTransactionIdOrderByLineNoAsc(Long storeId, String transactionId);

    List<Sale> findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
            Long storeId,
            Instant from,
            Instant to
    );
}
