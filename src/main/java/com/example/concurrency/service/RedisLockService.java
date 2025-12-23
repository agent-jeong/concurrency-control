package com.example.concurrency.service;

import com.example.concurrency.domain.Product;
import com.example.concurrency.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 분산 락 (Distributed Lock with Redis)
 * 
 * 특징:
 * - Redis의 Redisson 라이브러리 사용
 * - 애플리케이션 레벨에서 락 제어
 * - 여러 서버 환경에서 동작
 * 
 * 장점:
 * - MSA 환경에서 사용 가능
 * - DB 부하 없음
 * - TTL로 데드락 방지
 * 
 * 단점:
 * - Redis 의존성
 * - 네트워크 비용
 * - 락 획득/해제 오버헤드
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {
    
    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    
    public void decreaseStock(Long productId, int quantity) {
        String lockKey = "product:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 락 획득 시도 (최대 5초 대기, 10초 후 자동 해제)
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!acquired) {
                throw new IllegalStateException("락 획득 실패");
            }
            
            log.info("Redis 락 획득: {}", Thread.currentThread().getName());
            
            // 실제 비즈니스 로직
            decreaseStockInternal(productId, quantity);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중단", e);
        } finally {
            // 락 해제 (현재 스레드가 보유한 경우만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Redis 락 해제: {}", Thread.currentThread().getName());
            }
        }
    }
    
    @Transactional
    protected void decreaseStockInternal(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        product.decreaseStock(quantity);
        productRepository.saveAndFlush(product);
    }
}
