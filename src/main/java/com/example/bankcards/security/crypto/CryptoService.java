package com.example.bankcards.security.crypto;

public interface CryptoService {
    String encryptPan(String pan);
    String decryptPan(String ciphertext);
}
