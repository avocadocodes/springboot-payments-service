package com.nikita.payments.service;

import com.nikita.payments.domain.Payment;
import com.nikita.payments.domain.PaymentStatus;
import com.nikita.payments.dto.PaymentResponse;
import com.nikita.payments.exception.InvalidPaymentStateException;
import com.nikita.payments.gateway.GatewayResponse;
import com.nikita.payments.gateway.PaymentGatewayClient;
import com.nikita.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceRefundTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayClient gatewayClient;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, gatewayClient, idempotencyService);
    }

    @Test
    void refund_succeededPayment_transitionsToRefunded() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.SUCCEEDED);

        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gatewayClient.refund(any())).thenReturn(GatewayResponse.builder()
                .success(true).reference("REF-001").build());

        PaymentResponse response = paymentService.refundPayment(id);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.getGatewayReference()).isEqualTo("REF-001");
    }

    @Test
    void refund_pendingPayment_throwsInvalidState() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.PENDING);

        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(id))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("refund")
                .hasMessageContaining("PENDING");
    }

    @Test
    void refund_failedPayment_throwsInvalidState() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.FAILED);

        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(id))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void refund_alreadyRefunded_throwsInvalidState() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.REFUNDED);

        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(id))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("REFUNDED");
    }

    private Payment buildPayment(UUID id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmount(new BigDecimal("50.00"));
        p.setCurrency("USD");
        p.setCustomerId("cust-456");
        p.setMethod("CARD");
        p.setStatus(status);
        return p;
    }
}
