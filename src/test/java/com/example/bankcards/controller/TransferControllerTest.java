package com.example.bankcards.controller;

import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.security.AuthRateLimitFilter;
import com.example.bankcards.security.RestAuthEntryPoint;
import com.example.bankcards.security.jwt.JwtFilter;
import com.example.bankcards.service.TransferService;
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

@WebMvcTest(controllers = TransferController.class)
@AutoConfigureMockMvc(addFilters = true)
class TransferControllerTest {

    @Resource MockMvc mockMvc;
    @Resource ObjectMapper objectMapper;
    @MockBean TransferService transferService;
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
    @DisplayName("POST /api/transfers -> 200, проксирует userId и тело; Idempotency-Key из заголовка попадает в request")
    void initiate_ok_withIdempotencyHeader() throws Exception {
        when(transferService.initiate(eq(42L), any(TransferRequest.class)))
                .thenReturn(new TransferDto());

        var req = new TransferRequest();
        req.setFromCardId(111L);
        req.setToCardId(222L);
        req.setAmount(5_000L);

        mockMvc.perform(post("/api/transfers")
                        .with(SecurityTestUtils.customUser(42L))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-123")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService).initiate(eq(42L), captor.capture());
        var passed = captor.getValue();
        assertThat(passed.getIdempotencyKey()).isEqualTo("idem-123");
        assertThat(passed.getFromCardId()).isEqualTo(111L);
        assertThat(passed.getToCardId()).isEqualTo(222L);
        assertThat(passed.getAmount()).isEqualTo(5_000L);
    }


    @Test
    @DisplayName("POST /api/transfers без аутентификации -> 401")
    void initiate_unauthorized() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("POST /api/transfers/{id}:cancel -> 200, проксирует userId и transferId")
    void cancel_ok() throws Exception {
        when(transferService.cancel(77L, 1001L)).thenReturn(new TransferDto());

        mockMvc.perform(post("/api/transfers/{id}:cancel", 1001L)
                        .with(SecurityTestUtils.customUser(77L))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(transferService).cancel(77L, 1001L);
    }

    @Test
    @DisplayName("POST /api/transfers/{id}:cancel без аутентификации -> 401")
    void cancel_unauthorized() throws Exception {
        mockMvc.perform(post("/api/transfers/{id}:cancel", 1001L)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("GET /api/transfers/my -> 200, дефолты page=0 size=10")
    void listMy_defaults() throws Exception {
        when(transferService.listMy(5L, 0, 10)).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/transfers/my")
                        .with(SecurityTestUtils.customUser(5L)))
                .andExpect(status().isOk());

        verify(transferService).listMy(5L, 0, 10);
    }

    @Test
    @DisplayName("GET /api/transfers/my -> 200, кастомные page/size")
    void listMy_params() throws Exception {
        when(transferService.listMy(9L, 2, 25)).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/transfers/my")
                        .param("page", "2")
                        .param("size", "25")
                        .with(SecurityTestUtils.customUser(9L)))
                .andExpect(status().isOk());

        verify(transferService).listMy(9L, 2, 25);
    }

    @Test
    @DisplayName("GET /api/transfers/my без аутентификации -> 401")
    void listMy_unauthorized() throws Exception {
        mockMvc.perform(get("/api/transfers/my"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("GET /api/transfers (ADMIN) -> 200, дефолты page=0 size=10")
    void listAll_defaults_admin() throws Exception {
        when(transferService.listAll(0, 10)).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/transfers").with(SecurityTestUtils.admin()))
                .andExpect(status().isOk());

        verify(transferService).listAll(0, 10);
    }

    @Test
    @DisplayName("GET /api/transfers (ADMIN) -> 200, с page/size")
    void listAll_params_admin() throws Exception {
        when(transferService.listAll(3, 50)).thenReturn(new PageDto<>());

        mockMvc.perform(get("/api/transfers")
                        .param("page", "3")
                        .param("size", "50")
                        .with(SecurityTestUtils.admin()))
                .andExpect(status().isOk());

        verify(transferService).listAll(3, 50);
    }
}
