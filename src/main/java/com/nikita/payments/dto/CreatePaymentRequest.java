package com.nikita.payments.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount format invalid")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "currency must be uppercase ISO 4217")
    private String currency;

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "method is required")
    @Pattern(regexp = "CARD|ACH|WIRE|WALLET", message = "method must be one of CARD, ACH, WIRE, WALLET")
    private String method;
}
