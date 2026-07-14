package com.onclick.domain.sale.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.sale.entity.SaleStatus;
import com.onclick.domain.sale.entity.SaleTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(
            value = "SELECT sale.id FROM SaleTransaction sale WHERE sale.storeId = :storeId",
            countQuery = "SELECT COUNT(sale.id) FROM SaleTransaction sale WHERE sale.storeId = :storeId"
    )
    Page<Long> findPageIdsByStoreId(
            @Param("storeId") Long storeId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            SELECT DISTINCT sale
              FROM SaleTransaction sale
             WHERE sale.storeId = :storeId
               AND sale.id IN :saleIds
            """)
    List<SaleTransaction> findAllWithItemsByStoreIdAndIdIn(
            @Param("storeId") Long storeId,
            @Param("saleIds") Collection<Long> saleIds
    );

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
            @Param("cancelledAt") LocalDateTime cancelledAt
    );

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<SaleTransaction> findAllByStoreIdAndStatusAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtAsc(
            Long storeId,
            SaleStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    @EntityGraph(attributePaths = {"items"})
    List<SaleTransaction> findAllByStoreIdAndSoldAtGreaterThanEqualAndSoldAtLessThanEqualOrderBySoldAtAsc(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );

    @EntityGraph(attributePaths = {"items"})
    List<SaleTransaction> findAllByStoreIdAndSoldAtLessThanOrderBySoldAtAsc(
            Long storeId,
            LocalDateTime to
    );
}
