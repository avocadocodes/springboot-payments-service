package com.nikita.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nikita.payments.domain.Payment;
import com.nikita.payments.domain.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String customerId;
    private String method;
    private PaymentStatus status;
    private String gatewayReference;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    @JsonIgnore
    @Builder.Default
    private boolean idempotentReplay = false;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .customerId(p.getCustomerId())
                .method(p.getMethod())
                .status(p.getStatus())
                .gatewayReference(p.getGatewayReference())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    public static PaymentResponse fromReplay(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .customerId(p.getCustomerId())
                .method(p.getMethod())
                .status(p.getStatus())
                .gatewayReference(p.getGatewayReference())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .idempotentReplay(true)
                .build();
    }
}
