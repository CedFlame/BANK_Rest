package com.example.bankcards.security.crypto.impl;

import com.example.bankcards.config.properties.CryptoProperties;
import com.example.bankcards.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AesGcmCryptoService implements CryptoService {

    private final CryptoProperties props;
    private static final String ALG = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LEN_BITS = 128;
    private static final int IV_LEN_BYTES = 12;

    private SecretKey key() {
        byte[] k = props.getAesKey().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(k, ALG);
    }

    @Override
    public String encryptPan(String pan) {
        try {
            byte[] iv = new byte[IV_LEN_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(pan.getBytes(StandardCharsets.UTF_8));

            ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
            bb.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new IllegalStateException("PAN encryption failed", e);
        }
    }

    @Override
    public String decryptPan(String ciphertext) {
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LEN_BYTES];
            byte[] ct = new byte[data.length - IV_LEN_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_LEN_BYTES);
            System.arraycopy(data, IV_LEN_BYTES, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PAN decryption failed", e);
        }
    }
}
