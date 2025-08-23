package com.example.bankcards.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) { super("Transfer not found: " + id); }
}
