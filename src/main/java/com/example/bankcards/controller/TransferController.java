package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.config.OpenApiConfig;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Transfers", description = "Переводы между картами")
public class TransferController {

    private final TransferService transferService;

    @Operation(
            summary = "Инициировать перевод",
            description = """
                          Создает перевод между двумя картами текущего пользователя.
                          Для идемпотентности можно передать заголовок **Idempotency-Key**.
                          """,
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Перевод создан",
                    content = @Content(schema = @Schema(implementation = TransferDto.class)),
                    headers = @Header(name = "Idempotency-Key", description = "Переданный ключ идемпотентности (если был)")),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации/бизнес-правил"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Карта/пользователь не найдены"),
            @ApiResponse(responseCode = "409", description = "Конфликт идемпотентности")
    })
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public TransferDto initiate(
            @Parameter(description = "Ключ идемпотентности запроса", example = "idem-123")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(
                    required = true,
                    description = "Данные перевода (fromCardId, toCardId, amount, ttlSeconds)",
                    content = @Content(schema = @Schema(implementation = TransferRequest.class))
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody TransferRequest request
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return transferService.initiate(currentUserId(), request);
    }

    @Operation(
            summary = "Отменить перевод",
            description = "Отменяет *собственный* PENDING-перевод (пока не истёк).",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Перевод отменён",
                    content = @Content(schema = @Schema(implementation = TransferDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Не ваш перевод или неверное состояние"),
            @ApiResponse(responseCode = "404", description = "Перевод не найден")
    })
    @PostMapping("/{id}:cancel")
    @PreAuthorize("isAuthenticated()")
    public TransferDto cancel(
            @Parameter(description = "ID перевода", example = "1001")
            @PathVariable("id") Long transferId
    ) {
        return transferService.cancel(currentUserId(), transferId);
    }

    @Operation(
            summary = "Мои переводы",
            description = "Постраничный список переводов текущего пользователя.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public PageDto<TransferDto> listMy(
            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..50)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return transferService.listMy(currentUserId(), page, size);
    }

    @Operation(
            summary = "Список всех переводов (ADMIN)",
            description = "Постраничный список всех переводов в системе.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    @IsAdmin
    public PageDto<TransferDto> listAll(
            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..50)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return transferService.listAll(page, size);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails p)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return p.getId();
    }
}
