package com.example.orderflow.fulfillment.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ProcessedOrderStore {

    // Prefiks oddziela nasze klucze od innych danych w tym samym Redisie (np. koszyka z product-service).
    private static final String KEY_PREFIX = "fulfillment:processed:";

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public boolean claim(String orderId) {
        Boolean firstTime = redis.opsForValue().setIfAbsent(KEY_PREFIX + orderId, "1", TTL);
        return Boolean.TRUE.equals(firstTime);
    }

    public void release(String orderId) {
        redis.delete(KEY_PREFIX + orderId);
    }
}
