package com.onclick.domain.sale.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.sale.entity.SaleTransaction;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<SaleTransaction> findByStoreIdAndClientTransactionId(
            Long storeId,
            String clientTransactionId
    );

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<SaleTransaction> findByIdAndStoreId(Long id, Long storeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SaleTransaction sale
               SET sale.status = com.onclick.domain.sale.entity.SaleStatus.CANCELLED,
                   sale.cancelledAt = :cancelledAt
             WHERE sale.id = :saleId
               AND sale.storeId = :storeId
               AND sale.status = com.onclick.domain.sale.entity.SaleStatus.COMPLETED
            """)
    int cancelIfCompleted(
            @Param("saleId") Long saleId,
            @Param("storeId") Long storeId,
            @Param("cancelledAt") Instant cancelledAt
    );

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<SaleTransaction> findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
            Long storeId,
            Instant from,
            Instant to
    );
}
