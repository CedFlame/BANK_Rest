package com.example.bankcards.exception;

public class OwnershipViolationException extends RuntimeException {
    public OwnershipViolationException(String message) { super(message); }
}
