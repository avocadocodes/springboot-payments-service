package com.nikita.payments.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key conflict — request in flight for key: " + key);
    }
}
