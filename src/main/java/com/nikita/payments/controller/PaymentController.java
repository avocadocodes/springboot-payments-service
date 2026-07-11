package com.nikita.payments.controller;

import com.nikita.payments.dto.CreatePaymentRequest;
import com.nikita.payments.dto.PaymentResponse;
import com.nikita.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing operations")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(
        summary = "Create a payment",
        description = "Creates a new payment. Supply a unique Idempotency-Key header; repeating the same key returns the original result without double-charging."
    )
    @ApiResponse(responseCode = "201", description = "Payment created")
    @ApiResponse(responseCode = "200", description = "Idempotent replay — existing payment returned")
    @ApiResponse(responseCode = "400", description = "Validation error or missing header")
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Unique key to ensure idempotent processing", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentResponse response = paymentService.createPayment(idempotencyKey, request);
        return ResponseEntity.status(response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment by ID")
    @ApiResponse(responseCode = "200", description = "Payment found")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @PostMapping("/{id}/refund")
    @Operation(
        summary = "Refund a payment",
        description = "Initiates a refund for a SUCCEEDED payment. Returns 422 if the payment is in any other state."
    )
    @ApiResponse(responseCode = "200", description = "Refund processed")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "422", description = "Payment not in SUCCEEDED state")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
