package com.nikita.payments.service;

import com.nikita.payments.domain.Payment;
import com.nikita.payments.domain.PaymentStatus;
import com.nikita.payments.dto.CreatePaymentRequest;
import com.nikita.payments.dto.PaymentResponse;
import com.nikita.payments.gateway.GatewayResponse;
import com.nikita.payments.gateway.PaymentGatewayClient;
import com.nikita.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceIdempotencyTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayClient gatewayClient;

    private IdempotencyService idempotencyService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        Map<String, UUID> idempotencyStore = new HashMap<>();
        idempotencyService = new IdempotencyService(null) {
            @Override
            public Optional<UUID> findExistingPaymentId(String key) {
                return Optional.ofNullable(idempotencyStore.get(key));
            }

            @Override
            public void store(String key, UUID paymentId) {
                idempotencyStore.put(key, paymentId);
            }
        };

        paymentService = new PaymentService(paymentRepository, gatewayClient, idempotencyService);
    }

    @Test
    void createPayment_idempotentKey_doesNotChargeGatewayTwice() {
        String idempotencyKey = "idem-key-" + UUID.randomUUID();
        CreatePaymentRequest request = buildRequest();

        UUID savedId = UUID.randomUUID();
        Payment savedPayment = buildPayment(savedId, PaymentStatus.SUCCEEDED);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setStatus(PaymentStatus.PENDING);
                savedPayment.setId(savedId);
                return savedPayment;
            }
            return p;
        });
        when(paymentRepository.findById(savedId)).thenReturn(Optional.of(savedPayment));
        when(gatewayClient.charge(any())).thenReturn(GatewayResponse.builder()
                .success(true).reference("GW-ABC123").build());

        PaymentResponse first = paymentService.createPayment(idempotencyKey, request);
        PaymentResponse second = paymentService.createPayment(idempotencyKey, request);

        verify(gatewayClient, times(1)).charge(any());
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void createPayment_differentKeys_chargesGatewayForEach() {
        CreatePaymentRequest request = buildRequest();

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(gatewayClient.charge(any())).thenReturn(GatewayResponse.builder()
                .success(true).reference("GW-XYZ").build());

        paymentService.createPayment("key-1", request);
        paymentService.createPayment("key-2", request);

        verify(gatewayClient, times(2)).charge(any());
    }

    @Test
    void createPayment_gatewayFails_paymentMarkedFailed() {
        String idempotencyKey = "fail-key-" + UUID.randomUUID();
        CreatePaymentRequest request = buildRequest();

        UUID savedId = UUID.randomUUID();
        Payment savedPayment = buildPayment(savedId, PaymentStatus.PENDING);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(savedId);
            return p;
        });
        when(gatewayClient.charge(any())).thenReturn(GatewayResponse.builder()
                .success(false).errorMessage("Gateway timeout").build());

        PaymentResponse response = paymentService.createPayment(idempotencyKey, request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("Gateway timeout");
    }

    private CreatePaymentRequest buildRequest() {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("USD");
        req.setCustomerId("cust-123");
        req.setMethod("CARD");
        return req;
    }

    private Payment buildPayment(UUID id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmount(new BigDecimal("100.00"));
        p.setCurrency("USD");
        p.setCustomerId("cust-123");
        p.setMethod("CARD");
        p.setStatus(status);
        return p;
    }
}
