package com.onclick.domain.sale.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.onclick.domain.product.entity.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sale_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sale_transactions_store_client_id",
                columnNames = {"store_id", "client_transaction_id"}
        ),
        indexes = @Index(
                name = "idx_sale_transactions_store_sold_at",
                columnList = "store_id,sold_at"
        )
)
public class SaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "client_transaction_id", length = 100)
    private String clientTransactionId;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "saleTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<SaleItem> items = new ArrayList<>();

    protected SaleTransaction() {
    }

    private SaleTransaction(Long storeId, String clientTransactionId, Instant soldAt) {
        this.storeId = storeId;
        this.clientTransactionId = clientTransactionId;
        this.soldAt = databaseInstant(soldAt);
        this.status = SaleStatus.COMPLETED;
    }

    public static SaleTransaction create(
            Long storeId,
            String clientTransactionId,
            Instant soldAt
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

    public void cancel(Instant cancelledAt) {
        if (status == SaleStatus.CANCELLED) {
            return;
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = databaseInstant(cancelledAt);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = databaseInstant(Instant.now());
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

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getClientTransactionId() {
        return clientTransactionId;
    }

    public Instant getSoldAt() {
        return soldAt;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private static Instant databaseInstant(Instant value) {
        return Objects.requireNonNull(value, "instant must not be null")
                .truncatedTo(ChronoUnit.MICROS);
    }
}
