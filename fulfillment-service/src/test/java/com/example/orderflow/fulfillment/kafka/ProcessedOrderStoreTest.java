package com.example.orderflow.fulfillment.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessedOrderStore Unit Tests")
class ProcessedOrderStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ProcessedOrderStore store;

    @BeforeEach
    void setUp() {
        store = new ProcessedOrderStore(redis);
    }

    @Test
    @DisplayName("claim - returns true when key did not exist (first processing)")
    void claimReturnsTrueOnFirstTime() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("fulfillment:processed:order-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(store.claim("order-1")).isTrue();
    }

    @Test
    @DisplayName("claim - returns false when key already existed (duplicate)")
    void claimReturnsFalseOnDuplicate() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertThat(store.claim("order-1")).isFalse();
    }

    @Test
    @DisplayName("claim - treats null reply from Redis as not-claimed")
    void claimTreatsNullAsFalse() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(null);

        assertThat(store.claim("order-1")).isFalse();
    }

    @Test
    @DisplayName("release - deletes the dedup key")
    void releaseDeletesKey() {
        store.release("order-1");

        verify(redis).delete("fulfillment:processed:order-1");
    }
}
