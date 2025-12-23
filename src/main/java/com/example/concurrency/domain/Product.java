package com.example.concurrency.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    private Integer stock;
    
    @Version  // 낙관적 락을 위한 버전 필드
    private Long version;
    
    public Product(String name, Integer stock) {
        this.name = name;
        this.stock = stock;
    }
    
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalArgumentException("재고 부족: " + this.stock);
        }
        this.stock -= quantity;
    }
}
