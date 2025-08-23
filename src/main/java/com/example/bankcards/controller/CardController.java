// src/main/java/com/example/bankcards/controller/CardController.java
package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.CardService;
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
public class CardController {

    private final CardService cardService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public PageDto<CardDto> listMy(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) CardStatus status) {
        Long userId = currentUserId();
        CardFilter filter = new CardFilter();
        filter.setStatus(status);
        return cardService.listMy(userId, page, size, filter);
    }

    @IsAdmin
    @PostMapping("/{userId}")
    public CardDto createForUser(@PathVariable Long userId,
                                 @Valid @RequestBody CardCreateRequest request) {
        return cardService.createForUser(userId, request);
    }

    @IsAdmin
    @PatchMapping("/{id}:block")
    public CardDto block(@PathVariable Long id) {
        return cardService.block(id);
    }

    @IsAdmin
    @PatchMapping("/{id}:activate")
    public CardDto activate(@PathVariable Long id) {
        return cardService.activate(id);
    }

    @IsAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cardService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @IsAdmin
    @GetMapping
    public PageDto<CardDto> listAll(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) CardStatus status) {
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
