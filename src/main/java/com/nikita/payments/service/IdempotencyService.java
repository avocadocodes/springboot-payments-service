package com.nikita.payments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redis;

    @Value("${payments.idempotency.ttl-hours:24}")
    private long ttlHours;

    public Optional<UUID> findExistingPaymentId(String idempotencyKey) {
        String value = redis.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }

    public void store(String idempotencyKey, UUID paymentId) {
        redis.opsForValue().set(
                KEY_PREFIX + idempotencyKey,
                paymentId.toString(),
                Duration.ofHours(ttlHours)
        );
        log.debug("Stored idempotency key={} -> paymentId={}", idempotencyKey, paymentId);
    }
}
