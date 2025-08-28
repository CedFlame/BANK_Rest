package com.example.bankcards.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthAuditListener {

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent e) {
        if (e.getAuthentication() == null) return;
        log.info("Auth success: {}", e.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent e) {
        if (e.getAuthentication() == null) return;
        log.warn("Auth failed: {} ({})", e.getAuthentication().getName(),
                e.getException() != null ? e.getException().getClass().getSimpleName() : "unknown");
    }
}
