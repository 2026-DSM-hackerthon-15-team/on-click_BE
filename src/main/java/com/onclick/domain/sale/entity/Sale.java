package com.onclick.domain.sale.entity;

import java.time.Instant;

import com.onclick.domain.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sales",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_store_transaction_line",
                columnNames = {"store_id", "transaction_id", "line_no"}
        ),
        indexes = {
                @Index(name = "idx_sales_store_sold_at", columnList = "store_id,sold_at"),
                @Index(name = "idx_sales_store_transaction", columnList = "store_id,transaction_id")
        }
)
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 100)
    private String productNameSnapshot;

    @Column(name = "product_price_snapshot", nullable = false)
    private long productPriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Sale() {
    }

    private Sale(
            Long storeId,
            String transactionId,
            int lineNo,
            Product product,
            int quantity,
            long paidAmount,
            Instant soldAt
    ) {
        this.storeId = storeId;
        this.transactionId = transactionId;
        this.lineNo = lineNo;
        this.product = product;
        this.productNameSnapshot = product.getName();
        this.productPriceSnapshot = product.getPrice();
        this.quantity = quantity;
        this.paidAmount = paidAmount;
        this.soldAt = soldAt;
        this.status = SaleStatus.COMPLETED;
    }

    public static Sale create(
            Long storeId,
            String transactionId,
            int lineNo,
            Product product,
            int quantity,
            long paidAmount,
            Instant soldAt
    ) {
        return new Sale(storeId, transactionId, lineNo, product, quantity, paidAmount, soldAt);
    }

    public void cancel(Instant cancelledAt) {
        if (status == SaleStatus.CANCELLED) {
            return;
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public long getProductPriceSnapshot() {
        return productPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getPaidAmount() {
        return paidAmount;
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
}
