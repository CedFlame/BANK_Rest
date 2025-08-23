package com.example.bankcards.controller;

import com.example.bankcards.annotation.IsAdmin;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.TransferService;
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
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public TransferDto initiate(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody TransferRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return transferService.initiate(currentUserId(), request);
    }

    @PostMapping("/{id}:cancel")
    @PreAuthorize("isAuthenticated()")
    public TransferDto cancel(@PathVariable("id") Long transferId) {
        return transferService.cancel(currentUserId(), transferId);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public PageDto<TransferDto> listMy(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return transferService.listMy(currentUserId(), page, size);
    }

    @GetMapping
    @IsAdmin
    public PageDto<TransferDto> listAll(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
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
