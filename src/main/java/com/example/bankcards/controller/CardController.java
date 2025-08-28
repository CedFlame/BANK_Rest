package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.config.OpenApiConfig;
import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cards", description = "Операции с банковскими картами")
public class CardController {

    private final CardService cardService;

    @Operation(
            summary = "Мои карты",
            description = "Возвращает страницу карт текущего пользователя.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public PageDto<CardDto> listMy(
            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..100)", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Фильтр по статусу карты", example = "ACTIVE")
            @RequestParam(required = false) CardStatus status
    ) {
        Long userId = currentUserId();
        CardFilter filter = new CardFilter();
        filter.setStatus(status);
        return cardService.listMy(userId, page, size, filter);
    }

    @Operation(
            summary = "Создать карту пользователю (ADMIN)",
            description = "Создаёт карту для указанного пользователя.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Создано",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "409", description = "Карта уже существует")
    })
    @IsAdmin
    @PostMapping("/{userId}")
    public CardDto createForUser(
            @Parameter(description = "ID владельца карты", example = "5")
            @PathVariable Long userId,
            @RequestBody(
                    required = true,
                    description = "PAN в виде 16-значной строки и срок действия (YYYY-MM)",
                    content = @Content(schema = @Schema(implementation = CardCreateRequest.class))
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody CardCreateRequest request
    ) {
        return cardService.createForUser(userId, request);
    }

    @Operation(
            summary = "Заблокировать карту (ADMIN)",
            description = "Меняет статус карты на BLOCKED.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "409", description = "Неверное состояние карты или карта просрочена")
    })
    @IsAdmin
    @PatchMapping("/{id}:block")
    public CardDto block(
            @Parameter(description = "ID карты", example = "100")
            @PathVariable Long id
    ) {
        return cardService.block(id);
    }

    @Operation(
            summary = "Активировать карту (ADMIN)",
            description = "Меняет статус карты на ACTIVE.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "409", description = "Неверное состояние карты или карта просрочена")
    })
    @IsAdmin
    @PatchMapping("/{id}:activate")
    public CardDto activate(
            @Parameter(description = "ID карты", example = "101")
            @PathVariable Long id
    ) {
        return cardService.activate(id);
    }

    @Operation(
            summary = "Удалить карту (ADMIN)",
            description = "Удаляет карту по ID. Невозможно удалить, если по карте есть переводы.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Удалено"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "409", description = "Удаление запрещено (есть связанные переводы)")
    })
    @IsAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID карты", example = "300")
            @PathVariable Long id
    ) {
        cardService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Список всех карт (ADMIN)",
            description = "Постраничный список карт со статус-фильтром.",
            security = { @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER) }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @IsAdmin
    @GetMapping
    public PageDto<CardDto> listAll(
            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..100)", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Фильтр по статусу", example = "ACTIVE")
            @RequestParam(required = false) CardStatus status
    ) {
        CardFilter filter = new CardFilter();
        filter.setStatus(status);
        return cardService.listAll(page, size, filter);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails p)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return p.getId();
    }
}
