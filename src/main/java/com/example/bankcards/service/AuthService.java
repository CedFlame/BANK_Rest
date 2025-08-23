package com.example.bankcards.service;

import com.example.bankcards.dto.UserDto;

public interface AuthService {
    UserDto register(String username, String rawPassword);

    String login(String username, String rawPassword);

    UserDto me();
}
