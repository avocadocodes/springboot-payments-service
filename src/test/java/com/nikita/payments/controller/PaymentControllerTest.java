package com.nikita.payments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.payments.domain.PaymentStatus;
import com.nikita.payments.dto.CreatePaymentRequest;
import com.nikita.payments.dto.PaymentResponse;
import com.nikita.payments.exception.PaymentNotFoundException;
import com.nikita.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    void createPayment_validRequest_returns201() throws Exception {
        CreatePaymentRequest request = buildRequest();
        PaymentResponse response = buildResponse(UUID.randomUUID(), PaymentStatus.SUCCEEDED);

        when(paymentService.createPayment(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "test-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void createPayment_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_invalidAmount_returns400() throws Exception {
        CreatePaymentRequest bad = new CreatePaymentRequest();
        bad.setAmount(new BigDecimal("-1"));
        bad.setCurrency("USD");
        bad.setCustomerId("cust-1");
        bad.setMethod("CARD");

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "some-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void getPayment_existing_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentResponse response = buildResponse(id, PaymentStatus.SUCCEEDED);

        when(paymentService.getPayment(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getPayment_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.getPayment(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment Not Found"));
    }

    @Test
    void refundPayment_succeeded_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentResponse response = buildResponse(id, PaymentStatus.REFUNDED);

        when(paymentService.refundPayment(id)).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/{id}/refund", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void createPayment_invalidCurrency_returns400() throws Exception {
        CreatePaymentRequest bad = buildRequest();
        bad.setCurrency("us");

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "some-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    private CreatePaymentRequest buildRequest() {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setAmount(new BigDecimal("99.99"));
        req.setCurrency("USD");
        req.setCustomerId("cust-123");
        req.setMethod("CARD");
        return req;
    }

    private PaymentResponse buildResponse(UUID id, PaymentStatus status) {
        return PaymentResponse.builder()
                .id(id)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .customerId("cust-123")
                .method("CARD")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
