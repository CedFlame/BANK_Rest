package com.example.bankcards.service.impl;

import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.mapper.UserMapper;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.security.jwt.JwtProvider;
import com.example.bankcards.service.AuthService;
import com.example.bankcards.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;

    @Override
    @Transactional
    public UserDto register(String username, String rawPassword) {
        return userService.createUser(username, rawPassword, Set.of(Role.ROLE_USER));
    }

    @Override
    public String login(String username, String rawPassword) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username.trim(), rawPassword)
        );
        CustomUserDetails p = (CustomUserDetails) auth.getPrincipal();
        return jwtProvider.generateToken(p.getId(), p.getUsername(), p.getAuthorities());
    }

    @Override
    public UserDto me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails p)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return UserMapper.toDto(p.getUser());
    }
}
