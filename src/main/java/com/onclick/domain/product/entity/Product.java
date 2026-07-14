package com.onclick.domain.product.entity;

import java.time.LocalDateTime;

import com.onclick.common.time.KoreanTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;

    private Product(Long storeId, String name, long price) {
        this.storeId = storeId;
        this.name = name;
        this.price = price;
        this.active = true;
    }

    public static Product create(Long storeId, String name, long price) {
        return new Product(storeId, name, price);
    }

    public void update(String name, Long price) {
        if (name != null) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
        this.updatedAt = KoreanTime.now();
    }

    public void changeActive(boolean active) {
        this.active = active;
        this.updatedAt = KoreanTime.now();
    }

}
