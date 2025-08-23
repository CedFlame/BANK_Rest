package com.example.bankcards.exception;

public class InvalidCardStateException extends RuntimeException {
    public InvalidCardStateException(String message) { super(message); }
}
