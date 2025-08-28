package com.example.bankcards.exception;

public class CardDeletionNotAllowedException extends RuntimeException {
    public CardDeletionNotAllowedException(Long cardId) {
        super("Card cannot be deleted because it has related transfers: " + cardId);
    }
}
