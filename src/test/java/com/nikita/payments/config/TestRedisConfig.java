package com.nikita.payments.config;

import com.nikita.payments.service.IdempotencyService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public IdempotencyService idempotencyService() {
        return new IdempotencyService(null) {
            private final Map<String, UUID> store = new HashMap<>();

            @Override
            public Optional<UUID> findExistingPaymentId(String key) {
                return Optional.ofNullable(store.get(key));
            }

            @Override
            public void store(String key, UUID paymentId) {
                store.put(key, paymentId);
            }
        };
    }
}
