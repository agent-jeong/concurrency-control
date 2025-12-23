package com.example.concurrency.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.concurrency.domain.Product;
import com.example.concurrency.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 낙관적 락 (Optimistic Lock)
 * 
 * 특징:
 * - @Version을 이용한 버전 관리
 * - 충돌 시 ObjectOptimisticLockingFailureException 발생
 * - 재시도 로직 필요
 * 
 * 장점:
 * - DB 락을 사용하지 않아 성능 우수
 * - 충돌이 적은 환경에 적합
 * 
 * 단점:
 * - 충돌 시 재시도 필요
 * - 충돌이 많으면 성능 저하
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimisticLockService {
    
    private final ProductRepository productRepository;
    
    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        product.decreaseStock(quantity);
        productRepository.saveAndFlush(product);
        // version 자동 증가 (JPA가 UPDATE 시 WHERE version = ? 추가)
    }
    
    /**
     * 재시도 로직 포함 버전
     */
    public void decreaseStockWithRetry(Long productId, int quantity) {
        int maxRetries = 10;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                decreaseStock(productId, quantity);
                log.info("성공: {} 재시도", retryCount);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("낙관적 락 충돌 - 재시도 {}/{}", retryCount, maxRetries);
                
                if (retryCount >= maxRetries) {
                    throw new IllegalStateException("최대 재시도 초과", e);
                }
                
                try {
                    Thread.sleep(50); // 50ms 대기
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("재시도 중단", ie);
                }
            }
        }
    }
}
