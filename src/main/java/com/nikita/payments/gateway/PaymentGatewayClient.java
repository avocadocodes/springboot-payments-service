package com.nikita.payments.gateway;

import com.nikita.payments.domain.Payment;
import com.nikita.payments.exception.GatewayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
public class PaymentGatewayClient {

    private static final Random RANDOM = new Random();

    @Retry(name = "paymentGateway", fallbackMethod = "chargeFallback")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    public GatewayResponse charge(Payment payment) {
        simulateLatencyAndFailures(payment);

        String reference = "GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Gateway charge succeeded for payment={} ref={}", payment.getId(), reference);
        return GatewayResponse.builder()
                .success(true)
                .reference(reference)
                .build();
    }

    @Retry(name = "paymentGateway", fallbackMethod = "refundFallback")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "refundFallback")
    public GatewayResponse refund(Payment payment) {
        simulateLatencyAndFailures(payment);

        String reference = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Gateway refund succeeded for payment={} ref={}", payment.getId(), reference);
        return GatewayResponse.builder()
                .success(true)
                .reference(reference)
                .build();
    }

    public GatewayResponse chargeFallback(Payment payment, Throwable t) {
        log.warn("Gateway charge fallback triggered for payment={} reason={}", payment.getId(), t.getMessage());
        return GatewayResponse.builder()
                .success(false)
                .errorMessage("Gateway unavailable: " + t.getMessage())
                .build();
    }

    public GatewayResponse refundFallback(Payment payment, Throwable t) {
        log.warn("Gateway refund fallback triggered for payment={} reason={}", payment.getId(), t.getMessage());
        return GatewayResponse.builder()
                .success(false)
                .errorMessage("Gateway unavailable: " + t.getMessage())
                .build();
    }

    private void simulateLatencyAndFailures(Payment payment) {
        int roll = RANDOM.nextInt(10);
        if (roll == 0) {
            throw new GatewayException("Simulated gateway timeout");
        }
        if (roll == 1) {
            throw new GatewayException("Simulated gateway internal error");
        }
        try {
            Thread.sleep(RANDOM.nextInt(50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
