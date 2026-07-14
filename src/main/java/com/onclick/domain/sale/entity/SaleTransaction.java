package com.onclick.domain.sale.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.onclick.domain.product.entity.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "sale_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sale_transactions_store_client_id",
                columnNames = {"store_id", "client_transaction_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_sale_transactions_store_sold_at",
                        columnList = "store_id,sold_at,id"
                ),
                @Index(
                        name = "idx_sale_transactions_store_created_at_id",
                        columnList = "store_id,created_at,id"
                ),
                @Index(
                        name = "idx_sale_transactions_store_status_id",
                        columnList = "store_id,status,id"
                ),
                @Index(
                        name = "idx_sale_transactions_store_transaction_id",
                        columnList = "store_id,id"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "client_transaction_id", length = 100)
    private String clientTransactionId;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "saleTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Getter(AccessLevel.NONE)
    private List<SaleItem> items = new ArrayList<>();

    private SaleTransaction(Long storeId, String clientTransactionId, LocalDateTime soldAt) {
        this.storeId = storeId;
        this.clientTransactionId = clientTransactionId;
        this.soldAt = databaseDateTime(soldAt);
        this.status = SaleStatus.COMPLETED;
    }

    public static SaleTransaction create(
            Long storeId,
            String clientTransactionId,
            LocalDateTime soldAt
    ) {
        return new SaleTransaction(storeId, clientTransactionId, soldAt);
    }

    public void addItem(
            int lineNo,
            Product product,
            int quantity,
            long paidAmount
    ) {
        items.add(new SaleItem(this, lineNo, product, quantity, paidAmount));
    }

    public void cancel(LocalDateTime cancelledAt) {
        if (status == SaleStatus.CANCELLED) {
            return;
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = databaseDateTime(cancelledAt);
    }

    public long totalPaidAmount() {
        long total = 0;
        for (SaleItem item : items) {
            total = Math.addExact(total, item.getPaidAmount());
        }
        return total;
    }

    public int totalQuantity() {
        int total = 0;
        for (SaleItem item : items) {
            total = Math.addExact(total, item.getQuantity());
        }
        return total;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private static LocalDateTime databaseDateTime(LocalDateTime value) {
        return Objects.requireNonNull(value, "dateTime must not be null")
                .truncatedTo(ChronoUnit.MICROS);
    }
}
