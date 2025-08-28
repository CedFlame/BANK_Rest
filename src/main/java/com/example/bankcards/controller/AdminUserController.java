package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.dto.CreateUserAdminRequest;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UpdateRolesRequest;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin: Users", description = "Администрирование пользователей (только для роли ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class AdminUserController {

    private final UserService userService;

    @Operation(
            summary = "Список пользователей",
            description = "Возвращает страницу пользователей. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public PageDto<UserDto> list(
            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..100)", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Подстрока для поиска по username (email)")
            @RequestParam(required = false) String search
    ) {
        return userService.list(page, size, search);
    }

    @Operation(
            summary = "Получить пользователя по id",
            description = "Возвращает информацию о пользователе. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь найден",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/{id}")
    public UserDto getById(
            @Parameter(description = "ID пользователя", example = "42")
            @PathVariable Long id
    ) {
        return userService.getById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Operation(
            summary = "Создать пользователя",
            description = "Создаёт нового пользователя. Если роли не переданы — будет назначена ROLE_USER. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Создано",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "409", description = "Пользователь уже существует")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Данные для создания пользователя",
                    content = @Content(schema = @Schema(implementation = CreateUserAdminRequest.class))
            )
            @Valid @RequestBody CreateUserAdminRequest req
    ) {
        Set<Role> roles = (req.getRoles() == null || req.getRoles().isEmpty())
                ? EnumSet.of(Role.ROLE_USER)
                : EnumSet.copyOf(req.getRoles());
        return userService.createUser(req.getEmail(), req.getPassword(), roles);
    }

    @Operation(
            summary = "Обновить роли пользователя",
            description = "Полностью заменяет набор ролей пользователя. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PatchMapping("/{id}/roles")
    public UserDto updateRoles(
            @Parameter(description = "ID пользователя", example = "7")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Новый набор ролей",
                    content = @Content(schema = @Schema(implementation = UpdateRolesRequest.class))
            )
            @Valid @RequestBody UpdateRolesRequest req
    ) {
        return userService.updateRoles(id, req.getRoles());
    }

    @Operation(
            summary = "Удалить пользователя",
            description = "Удаляет пользователя по id. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Удалён"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID пользователя", example = "9")
            @PathVariable Long id
    ) {
        userService.delete(id);
    }

    @Operation(
            summary = "Проверить существование пользователя по username/email",
            description = "Возвращает флаг { exists: true|false }. Доступно только ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Невалидный email"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/exists")
    public Map<String, Boolean> exists(
            @Parameter(description = "Email (username) для проверки", example = "user@example.com")
            @RequestParam("username") @Email @Size(max = 254) String username
    ) {
        return Map.of("exists", userService.existsByUsername(username));
    }
}
