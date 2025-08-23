package com.example.bankcards.service;

import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.enums.Role;

import java.util.Optional;
import java.util.Set;

public interface UserService {
    UserDto createUser(String username, String rawPassword, Set<Role> roles);

    Optional<UserDto> getById(Long id);

    Optional<UserDto> getByUsername(String username);

    UserDto updateRoles(Long userId, Set<Role> roles);

    void delete(Long userId);

    PageDto<UserDto> list(int page, int size, String search);

    boolean existsByUsername(String username);
}
