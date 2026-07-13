package com.onclick.domain.sale.entity;

import com.onclick.domain.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sale_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sale_items_transaction_line",
                columnNames = {"sale_transaction_id", "line_no"}
        ),
        indexes = @Index(
                name = "idx_sale_items_transaction",
                columnList = "sale_transaction_id"
        )
)
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_transaction_id", nullable = false)
    private SaleTransaction saleTransaction;

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

    protected SaleItem() {
    }

    SaleItem(
            SaleTransaction saleTransaction,
            int lineNo,
            Product product,
            int quantity,
            long paidAmount
    ) {
        this.saleTransaction = saleTransaction;
        this.lineNo = lineNo;
        this.product = product;
        this.productNameSnapshot = product.getName();
        this.productPriceSnapshot = product.getPrice();
        this.quantity = quantity;
        this.paidAmount = paidAmount;
    }

    public Long getId() {
        return id;
    }

    public SaleTransaction getSaleTransaction() {
        return saleTransaction;
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
}
