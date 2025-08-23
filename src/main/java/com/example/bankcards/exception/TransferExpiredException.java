package com.example.bankcards.exception;

public class TransferExpiredException extends RuntimeException {
    public TransferExpiredException(Long id) { super("Transfer expired: " + id); }
}
