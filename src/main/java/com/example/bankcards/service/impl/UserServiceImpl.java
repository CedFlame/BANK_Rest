package com.example.bankcards.service.impl;

import com.example.bankcards.config.properties.UsersProperties;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.BadRequestException;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.mapper.UserMapper;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UsersProperties usersProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    @Override
    @Transactional
    public UserDto createUser(String username, String rawPassword, Set<Role> roles) {
        String u = normalize(username);
        validateUsername(u);
        validatePassword(rawPassword);
        ensureUserUnique(u);

        Set<Role> safeRoles = toSafeRoles(roles);
        User user = buildUser(u, rawPassword, safeRoles);

        user = userRepository.save(user);
        log.info("User created: {} roles={}", user.getUsername(), user.getRoles());
        return UserMapper.toDto(user);
    }

    @Override
    public Optional<UserDto> getById(Long id) {
        if (id == null) return Optional.empty();
        return userRepository.findById(id).map(UserMapper::toDto);
    }

    @Override
    public Optional<UserDto> getByUsername(String username) {
        String u = normalize(username);
        if (u == null || u.isBlank()) return Optional.empty();
        return userRepository.findByUsername(u).map(UserMapper::toDto);
    }

    @Override
    @Transactional
    public UserDto updateRoles(Long userId, Set<Role> roles) {
        if (userId == null) throw new BadRequestException("userId is null");
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setRoles(toSafeRoles(roles));
        user = userRepository.save(user);
        log.info("User roles updated: {} -> {}", user.getUsername(), user.getRoles());
        return UserMapper.toDto(user);
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        if (userId == null) throw new BadRequestException("userId is null");
        if (!userRepository.existsById(userId)) throw new UserNotFoundException(userId);
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
    }

    @Override
    public PageDto<UserDto> list(int page, int size, String search) {
        Pageable pageable = pageable(page, size);
        Page<User> usersPage = findUsersPage(search, pageable);
        List<UserDto> dtos = usersPage.stream().map(UserMapper::toDto).toList();
        return PageDtoMapper.toPageDto(usersPage, dtos);
    }

    @Override
    public boolean existsByUsername(String username) {
        String u = normalize(username);
        return u != null && !u.isBlank() && userRepository.existsByUsername(u);
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void validateUsername(String u) {
        if (u == null || u.isBlank()) throw new BadRequestException("username is blank");
    }

    private static void validatePassword(String p) {
        if (p == null || p.isBlank()) throw new BadRequestException("password is blank");
    }

    private void ensureUserUnique(String username) {
        if (userRepository.existsByUsername(username)) throw new UserAlreadyExistsException(username);
    }

    private static Set<Role> toSafeRoles(Set<Role> roles) {
        return (roles == null || roles.isEmpty()) ? EnumSet.of(Role.ROLE_USER) : EnumSet.copyOf(roles);
    }

    private User buildUser(String username, String rawPassword, Set<Role> roles) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .roles(roles)
                .build();
    }

    private Pageable pageable(int page, int size) {
        int pageIndex = Math.max(page, 0);
        int pageSize = clamp(size, usersProperties.getDefaultPageSize(), usersProperties.getMaxPageSize());
        return PageRequest.of(pageIndex, pageSize, DEFAULT_SORT);
    }

    private static int clamp(int requested, int defaultSize, int maxSize) {
        if (requested <= 0) return defaultSize;
        return Math.min(requested, maxSize);
    }

    private Page<User> findUsersPage(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByUsernameContainingIgnoreCase(search.trim(), pageable);
    }
}
