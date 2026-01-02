# 동시성 제어 방식 비교

## 시나리오
한정판 상품 100개를 100명이 동시에 구매 시도

## 구현된 방식

### 1. 낙관적 락 (Optimistic Lock)
```java
@Entity
public class Product {
    @Version  // 핵심
    private Long version;
}

// 사용
product.decreaseStock(1);  // UPDATE ... WHERE id=? AND version=?
```

**실행 쿼리**
```sql
UPDATE product 
SET stock=?, version=version+1 
WHERE id=? AND version=?  -- version이 다르면 예외 발생
```

### 2. 비관적 락 (Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
```

**실행 쿼리**
```sql
SELECT * FROM product WHERE id=? FOR UPDATE  -- 다른 트랜잭션 대기
```

### 3. Redis 분산 락 (Distributed Lock)
```java
RLock lock = redissonClient.getLock("product:lock:" + productId);
boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
```

**동작 방식**
```
Thread 1: lock.tryLock() -> 성공 -> 재고 감소 -> unlock()
Thread 2: lock.tryLock() -> 대기 -> 성공 -> 재고 감소 -> unlock()
```

## 실행 방법

### 1. Redis 실행
```bash
docker run -d -p 6379:6379 redis
```

### 2. 프로젝트 빌드 & 테스트
```bash
# 빌드
./gradlew build

# 전체 테스트
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests OptimisticLockTest
./gradlew test --tests PessimisticLockTest
./gradlew test --tests RedisLockTest
./gradlew test --tests ConcurrencyControlTest

# 테스트 결과 확인
./gradlew test --info
```

### Windows 사용자
```bash
gradlew.bat test
```

## 비교표

| 구분 | 낙관적 락 | 비관적 락 | Redis 분산 락 |
|------|------------|-----------|--------------|
| **락 방식** | 버전 체크 | SELECT FOR UPDATE | Redis 락 |
| **충돌 시** | 예외 발생 → 재시도 | 대기 | 대기 |
| **성능** | ⭐⭐⭐ (충돌 적을 때) | ⭐⭐ | ⭐⭐ |
| **DB 부하** | 낮음 | 높음 | 낮음 |
| **재시도** | 필요 | 불필요 | 불필요 |
| **데드락** | 없음 | 가능성 있음 | TTL로 방지 |
| **MSA 환경** | ❌ (단일 DB만) | ❌ (단일 DB만) | ✅ |
| **추가 인프라** | 불필요 | 불필요 | Redis 필요 |

## 성능 비교 (100개 요청 기준)

```
동시성 제어 없음: 실패 (90~95개 재고 남음)
낙관적 락: 500~800ms (재시도 포함)
비관적 락: 300~500ms
Redis 분산 락: 400~600ms
```

## 실무 선택 가이드

### 낙관적 락 사용
- 충돌이 적은 경우 (조회 > 수정)
- 성능이 중요한 경우
- 예: 게시글 수정, 프로필 업데이트

### 비관적 락 사용
- 충돌이 많은 경우
- 데이터 정합성이 최우선
- 단일 서버/DB 환경
- 예: 재고 차감, 포인트 차감

### Redis 분산 락 사용
- MSA 환경
- 여러 서버에서 동일 자원 접근
- DB 락을 피하고 싶은 경우
- 예: 쿠폰 발급, 선착순 이벤트

## 코드 흐름 비교

### 낙관적 락
```
1. 조회 (version=1)
2. 재고 감소
3. UPDATE ... WHERE version=1
4. 다른 스레드가 먼저 업데이트 → version=2
5. 현재 스레드 실패 (OptimisticLockingFailureException)
6. 재시도
```

### 비관적 락
```
1. SELECT ... FOR UPDATE (락 획득)
2. 다른 스레드는 대기
3. 재고 감소
4. COMMIT (락 해제)
5. 대기 중인 스레드가 락 획득
```

### Redis 분산 락
```
1. lock.tryLock() 시도
2. 성공 시 재고 감소
3. 실패 시 5초 대기
4. unlock()
5. 다음 스레드가 락 획득
```

## 프로젝트 구조
```
concurrency-control/
├── build.gradle                  # Gradle 빌드 설정
├── settings.gradle               # 프로젝트 설정
├── gradlew                       # Unix용 실행 스크립트
├── gradlew.bat                   # Windows용 실행 스크립트
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── src/
    ├── main/java/com/example/concurrency/
    │   ├── ConcurrencyApplication.java
    │   ├── domain/
    │   │   └── Product.java
    │   ├── repository/
    │   │   └── ProductRepository.java
    │   ├── service/
    │   │   ├── OptimisticLockService.java
    │   │   ├── PessimisticLockService.java
    │   │   └── RedisLockService.java
    │   └── config/
    │       └── RedisConfig.java
    └── test/
        └── ConcurrencyControlTest.java
```

## 주요 파일
- `build.gradle` - 의존성 및 빌드 설정
- `Product.java` - @Version 필드 포함 엔티티
- `OptimisticLockService.java` - 재시도 로직 구현
- `PessimisticLockService.java` - FOR UPDATE 사용
- `RedisLockService.java` - Redisson 활용
- `ConcurrencyControlTest.java` - 100개 동시 요청 테스트
