package com.example.bankcards.exception;

public class CardAlreadyExistsException extends RuntimeException {
    public CardAlreadyExistsException() {
        super("Card already exists");
    }
}
