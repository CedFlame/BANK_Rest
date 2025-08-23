package com.example.bankcards.exception;

public class CardExpiredException extends RuntimeException {
    public CardExpiredException(Long id) {
        super("Card expired: " + id);
    }
}
