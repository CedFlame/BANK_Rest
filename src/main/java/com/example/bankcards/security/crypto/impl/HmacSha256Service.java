package com.example.bankcards.security.crypto.impl;

import com.example.bankcards.config.properties.CryptoProperties;
import com.example.bankcards.security.crypto.HmacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class HmacSha256Service implements HmacService {

    private final CryptoProperties props;

    @Override
    public String hmacHex(String pan) {
        try {
            byte[] k = props.hmacKeyBytes();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(k, "HmacSHA256"));
            byte[] bytes = mac.doFinal(pan.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("PAN HMAC failed", e);
        }
    }
}
