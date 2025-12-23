package com.example.concurrency.service;

import com.example.concurrency.domain.Product;
import com.example.concurrency.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비관적 락 (Pessimistic Lock)
 * 
 * 특징:
 * - DB의 SELECT FOR UPDATE 사용
 * - 트랜잭션 종료 시까지 락 유지
 * - 다른 트랜잭션은 대기
 * 
 * 장점:
 * - 충돌이 확실히 방지됨
 * - 재시도 로직 불필요
 * - 데이터 정합성 보장
 * 
 * 단점:
 * - 성능 저하 (대기 시간)
 * - 데드락 가능성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PessimisticLockService {
    
    private final ProductRepository productRepository;
    
    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        // SELECT ... FOR UPDATE 실행
        Product product = productRepository.findByIdWithPessimisticLock(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        log.info("비관적 락 획득: {}", Thread.currentThread().getName());

        product.decreaseStock(quantity);
        productRepository.saveAndFlush(product);

        // 트랜잭션 커밋 시 락 해제
    }
}
