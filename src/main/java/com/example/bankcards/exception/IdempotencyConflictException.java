package com.example.bankcards.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Idempotency key conflict"); }
}
