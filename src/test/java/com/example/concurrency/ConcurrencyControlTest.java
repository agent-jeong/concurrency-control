package com.example.concurrency;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.concurrency.domain.Product;
import com.example.concurrency.repository.ProductRepository;
import com.example.concurrency.service.OptimisticLockService;
import com.example.concurrency.service.PessimisticLockService;

@SpringBootTest
class ConcurrencyControlTest {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private OptimisticLockService optimisticLockService;

    @Autowired
    private PessimisticLockService pessimisticLockService;
    
    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }
    
    /**
     * 동시성 제어 없음 - 실패 케이스
     */
    @Test
    @DisplayName("동시성 제어 없음 - 100개 요청, 재고 100 -> 예상: 0, 실제: 90+ (race condition)")
    void noLock() throws InterruptedException {
        // Given
        Product product = new Product("한정판 신발", 100);
        productRepository.save(product);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            // When
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        Product p = productRepository.findById(product.getId()).orElseThrow();
                        p.decreaseStock(1);
                        productRepository.save(p);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // Then
            Product result = productRepository.findById(product.getId()).orElseThrow();
            System.out.println("동시성 제어 없음 - 최종 재고: " + result.getStock());
            assertThat(result.getStock()).isGreaterThan(0); // Race condition 발생
        } finally {
            executorService.shutdown();
        }
    }
    
    /**
     * 낙관적 락 - 재시도 포함
     */
    @Test
    @DisplayName("낙관적 락 - 100개 요청, 재고 100 -> 예상: 0")
    void optimisticLock() throws InterruptedException {
        // Given
        Product product = new Product("한정판 신발", 100);
        productRepository.save(product);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        try {
            // When
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        optimisticLockService.decreaseStockWithRetry(product.getId(), 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.out.println("실패: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            long endTime = System.currentTimeMillis();

            // Then
            Product result = productRepository.findById(product.getId()).orElseThrow();
            System.out.println("낙관적 락 - 최종 재고: " + result.getStock());
            System.out.println("낙관적 락 - 성공 수: " + successCount.get());
            System.out.println("낙관적 락 - 소요 시간: " + (endTime - startTime) + "ms");

            assertThat(result.getStock()).isEqualTo(0);
        } finally {
            executorService.shutdown();
        }
    }

	/**
  * 비관적 락
  */
 @Test
 @DisplayName("비관적 락 - 100개 요청, 재고 100 -> 예상: 0")
 void pessimisticLock() throws InterruptedException {
     // Given
     Product product = new Product("한정판 신발", 100);
     productRepository.save(product);

     int threadCount = 100;
     ExecutorService executorService = Executors.newFixedThreadPool(32);
     CountDownLatch latch = new CountDownLatch(threadCount);

     try {
         // When
         long startTime = System.currentTimeMillis();

         for (int i = 0; i < threadCount; i++) {
             executorService.submit(() -> {
                 try {
                     pessimisticLockService.decreaseStock(product.getId(), 1);
                 } finally {
                     latch.countDown();
                 }
             });
         }

         latch.await();
         long endTime = System.currentTimeMillis();

         // Then
         Product result = productRepository.findById(product.getId()).orElseThrow();
         System.out.println("비관적 락 - 최종 재고: " + result.getStock());
         System.out.println("비관적 락 - 소요 시간: " + (endTime - startTime) + "ms");

         assertThat(result.getStock()).isEqualTo(0);
     } finally {
         executorService.shutdown();
     }
 }
}
