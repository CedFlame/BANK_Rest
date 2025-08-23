package com.example.bankcards.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("authz")
public class Authz {
    public boolean isAdmin(Authentication a) {
        return a != null && a.getAuthorities().stream()
                .anyMatch(ga -> "ROLE_ADMIN".equals(ga.getAuthority()));
    }
    public boolean isSelfOrAdmin(Authentication a, Long userId) {
        if (a == null) return false;
        if (isAdmin(a)) return true;
        Object p = a.getPrincipal();
        if (p instanceof com.example.bankcards.security.CustomUserDetails cud) {
            return cud.getId().equals(userId);
        }
        return false;
    }
}