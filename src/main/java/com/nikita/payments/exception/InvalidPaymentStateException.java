package com.nikita.payments.exception;

import com.nikita.payments.domain.PaymentStatus;

import java.util.UUID;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(UUID id, PaymentStatus current, String operation) {
        super("Cannot perform '" + operation + "' on payment " + id + " in state " + current);
    }
}
