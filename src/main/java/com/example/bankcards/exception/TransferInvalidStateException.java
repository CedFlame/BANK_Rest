package com.example.bankcards.exception;

public class TransferInvalidStateException extends RuntimeException {
    public TransferInvalidStateException(String message) { super(message); }
}
