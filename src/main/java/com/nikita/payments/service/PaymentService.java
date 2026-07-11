package com.nikita.payments.service;

import com.nikita.payments.domain.Payment;
import com.nikita.payments.domain.PaymentStatus;
import com.nikita.payments.dto.CreatePaymentRequest;
import com.nikita.payments.dto.PaymentResponse;
import com.nikita.payments.exception.InvalidPaymentStateException;
import com.nikita.payments.exception.PaymentNotFoundException;
import com.nikita.payments.gateway.GatewayResponse;
import com.nikita.payments.gateway.PaymentGatewayClient;
import com.nikita.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient gatewayClient;
    private final IdempotencyService idempotencyService;

    @Transactional
    public PaymentResponse createPayment(String idempotencyKey, CreatePaymentRequest request) {
        Optional<UUID> existing = idempotencyService.findExistingPaymentId(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={} -> paymentId={}", idempotencyKey, existing.get());
            Payment payment = paymentRepository.findById(existing.get())
                    .orElseThrow(() -> new PaymentNotFoundException(existing.get()));
            return PaymentResponse.fromReplay(payment);
        }

        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerId(request.getCustomerId());
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        GatewayResponse gatewayResponse = gatewayClient.charge(payment);

        if (gatewayResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference(gatewayResponse.getReference());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.getErrorMessage());
        }

        payment = paymentRepository.save(payment);
        idempotencyService.store(idempotencyKey, payment.getId());

        log.info("Payment created id={} status={}", payment.getId(), payment.getStatus());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStateException(id, payment.getStatus(), "refund");
        }

        GatewayResponse gatewayResponse = gatewayClient.refund(payment);

        if (gatewayResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setGatewayReference(gatewayResponse.getReference());
        } else {
            payment.setFailureReason(gatewayResponse.getErrorMessage());
            log.warn("Refund failed for payment={}: {}", id, gatewayResponse.getErrorMessage());
        }

        payment = paymentRepository.save(payment);
        log.info("Refund processed for payment={} status={}", id, payment.getStatus());
        return PaymentResponse.from(payment);
    }
}
