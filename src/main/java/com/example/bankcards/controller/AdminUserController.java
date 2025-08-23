package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.dto.CreateUserAdminRequest;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UpdateRolesRequest;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@IsAdmin
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public PageDto<UserDto> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) String search) {
        return userService.list(page, size, search);
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable Long id) {
        return userService.getById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserAdminRequest req) {
        Set<Role> roles = (req.getRoles() == null || req.getRoles().isEmpty())
                ? EnumSet.of(Role.ROLE_USER)
                : EnumSet.copyOf(req.getRoles());
        return userService.createUser(req.getEmail(), req.getPassword(), roles);
    }

    @PatchMapping("/{id}/roles")
    public UserDto updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest req) {
        return userService.updateRoles(id, req.getRoles());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @GetMapping("/exists")
    public Map<String, Boolean> exists(@RequestParam("email") @Email @Size(max = 254) String email) {
        return Map.of("exists", userService.existsByUsername(email));
    }
}
