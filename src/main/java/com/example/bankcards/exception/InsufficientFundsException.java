package com.example.bankcards.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long cardId) { super("Insufficient funds on card: " + cardId); }
}
