package com.example.bankcards.controller;

import com.example.bankcards.dto.LoginRequest;
import com.example.bankcards.dto.LoginResponse;
import com.example.bankcards.dto.RegisterRequest;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.example.bankcards.config.OpenApiConfig.SECURITY_SCHEME_BEARER;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Auth", description = "Аутентификация и профиль")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Регистрация",
            description = "Создаёт нового пользователя по email/username и паролю.",
            requestBody = @RequestBody(
                    required = true,
                    description = "Данные для регистрации",
                    content = @Content(schema = @Schema(implementation = RegisterRequest.class))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "409", description = "Пользователь уже существует")
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.getUsername(), request.getPassword());
    }

    @Operation(
            summary = "Логин",
            description = "Возвращает JWT токен в полях tokenType/accessToken.",
            requestBody = @RequestBody(
                    required = true,
                    description = "Учетные данные",
                    content = @Content(schema = @Schema(implementation = LoginRequest.class))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String jwt = authService.login(request.getUsername(), request.getPassword());
        return new LoginResponse("Bearer", jwt);
    }

    @Operation(
            summary = "Текущий пользователь",
            description = "Возвращает профиль текущего аутентифицированного пользователя.",
            security = { @SecurityRequirement(name = SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public UserDto me() {
        return authService.me();
    }
}
