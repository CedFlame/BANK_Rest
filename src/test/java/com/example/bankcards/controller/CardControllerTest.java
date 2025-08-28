package com.example.bankcards.controller;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.entity.enums.CardStatus;
import com.example.bankcards.security.AuthRateLimitFilter;
import com.example.bankcards.security.RestAuthEntryPoint;
import com.example.bankcards.security.jwt.JwtFilter;
import com.example.bankcards.service.CardService;
import com.example.bankcards.testutil.SecurityTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CardController.class)
@AutoConfigureMockMvc(addFilters = true)
class CardControllerTest {

    @Resource MockMvc mockMvc;
    @Resource ObjectMapper objectMapper;

    @MockBean CardService cardService;

    @MockBean JwtFilter jwtFilter;
    @MockBean AuthRateLimitFilter authRateLimitFilter;
    @MockBean RestAuthEntryPoint restAuthEntryPoint;

    @BeforeEach
    void passThroughFilters() throws Exception {
        Answer<Void> pass = inv -> {
            ServletRequest req = inv.getArgument(0);
            ServletResponse res = inv.getArgument(1);
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(req, res);
            return null;
        };
        doAnswer(pass).when(jwtFilter).doFilter(any(), any(), any());
        doAnswer(pass).when(authRateLimitFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/cards/my -> берёт id CustomUserDetails и передаёт status в фильтр")
    void listMy_ok() throws Exception {
        PageDto<CardDto> resp = new PageDto<>();
        when(cardService.listMy(eq(77L), anyInt(), anyInt(), any(CardFilter.class))).thenReturn(resp);

        mockMvc.perform(get("/api/cards/my")
                        .param("page", "1")
                        .param("size", "50")
                        .param("status", "BLOCKED")
                        .with(SecurityTestUtils.customUser(77L)))
                .andExpect(status().isOk());

        var fCap = ArgumentCaptor.forClass(CardFilter.class);
        verify(cardService).listMy(eq(77L), eq(1), eq(50), fCap.capture());
        assertThat(fCap.getValue().getStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    @DisplayName("GET /api/cards/my -> дефолты page=0, size=10, status=null")
    void listMy_defaults() throws Exception {
        when(cardService.listMy(eq(5L), anyInt(), anyInt(), any(CardFilter.class))).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/cards/my")
                        .with(SecurityTestUtils.customUser(5L)))
                .andExpect(status().isOk());

        var fCap = ArgumentCaptor.forClass(CardFilter.class);
        verify(cardService).listMy(eq(5L), eq(0), eq(10), fCap.capture());
        assertThat(fCap.getValue().getStatus()).isNull();
    }

    @Test
    @DisplayName("GET /api/cards/my без аутентификации -> 401 (security chain)")
    void listMy_unauthorized_401() throws Exception {
        mockMvc.perform(get("/api/cards/my"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cardService);
    }

    @Test
    @DisplayName("POST /api/cards/{userId} -> проксирует тело и userId в сервис (валидное тело)")
    void createForUser_ok() throws Exception {
        when(cardService.createForUser(eq(5L), any(CardCreateRequest.class))).thenReturn(new CardDto());

        CardCreateRequest req = new CardCreateRequest();
        req.setPan("4111111111111111");
        req.setExpiry("2030-12"); // формат YYYY-MM согласно валидации

        mockMvc.perform(post("/api/cards/{userId}", 5L)
                        .with(SecurityTestUtils.admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        var rCap = ArgumentCaptor.forClass(CardCreateRequest.class);
        verify(cardService).createForUser(eq(5L), rCap.capture());
        assertThat(rCap.getValue().getPan()).isEqualTo("4111111111111111");
        assertThat(rCap.getValue().getExpiry()).isEqualTo("2030-12");
    }

    @Test
    @DisplayName("POST /api/cards/{userId} -> 400 при нарушении валидации тела")
    void createForUser_validation400() throws Exception {
        String body = """
          {"pan":"4111111111111111","expiry":"12/30"}
        """;

        mockMvc.perform(post("/api/cards/{userId}", 5L)
                        .with(SecurityTestUtils.admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cardService);
    }

    @Test
    @DisplayName("PATCH /api/cards/{id}:block -> вызывает service.block и отдаёт 200")
    void block_ok() throws Exception {
        when(cardService.block(100L)).thenReturn(new CardDto());

        mockMvc.perform(patch("/api/cards/{id}:block", 100L)
                        .with(SecurityTestUtils.admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(cardService).block(100L);
    }

    @Test
    @DisplayName("PATCH /api/cards/{id}:activate -> вызывает service.activate и отдаёт 200")
    void activate_ok() throws Exception {
        when(cardService.activate(101L)).thenReturn(new CardDto());

        mockMvc.perform(patch("/api/cards/{id}:activate", 101L)
                        .with(SecurityTestUtils.admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(cardService).activate(101L);
    }

    @Test
    @DisplayName("DELETE /api/cards/{id} -> 204 и вызывает service.delete")
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/cards/{id}", 300L)
                        .with(SecurityTestUtils.admin())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(cardService).delete(300L);
    }

    @Test
    @DisplayName("GET /api/cards -> проксирует page/size/status в сервис")
    void listAll_ok() throws Exception {
        when(cardService.listAll(anyInt(), anyInt(), any(CardFilter.class))).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/cards")
                        .param("page", "2")
                        .param("size", "25")
                        .param("status", "ACTIVE")
                        .with(SecurityTestUtils.admin()))
                .andExpect(status().isOk());

        var fCap = ArgumentCaptor.forClass(CardFilter.class);
        verify(cardService).listAll(eq(2), eq(25), fCap.capture());
        assertThat(fCap.getValue().getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("GET /api/cards -> дефолты page=0/size=10/status=null")
    void listAll_defaults() throws Exception {
        when(cardService.listAll(anyInt(), anyInt(), any(CardFilter.class))).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/cards")
                        .with(SecurityTestUtils.admin()))
                .andExpect(status().isOk());

        var fCap = ArgumentCaptor.forClass(CardFilter.class);
        verify(cardService).listAll(eq(0), eq(10), fCap.capture());
        assertThat(fCap.getValue().getStatus()).isNull();
    }
}
