package com.example.bankcards.exception;

public class CardOwnershipException extends RuntimeException {
    public CardOwnershipException() { super("You are not the card owner"); }
}
