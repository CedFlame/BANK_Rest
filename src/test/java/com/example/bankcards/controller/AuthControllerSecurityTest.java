package com.example.bankcards.controller;

import com.example.bankcards.config.SecurityConfig;
import com.example.bankcards.config.properties.CorsProperties;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.exception.GlobalExceptionHandler;
import com.example.bankcards.security.AuthRateLimitFilter;
import com.example.bankcards.security.RestAuthEntryPoint;
import com.example.bankcards.security.jwt.JwtFilter;
import com.example.bankcards.service.AuthService;
import com.example.bankcards.testutil.SecurityTestUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthControllerSecurityTest.TestSecurityBeans.class})
class AuthControllerSecurityTest {

    @Resource
    private MockMvc mockMvc;

    @MockBean private AuthService authService;

    @MockBean private JwtFilter jwtFilter;
    @MockBean private AuthRateLimitFilter authRateLimitFilter;
    @MockBean private RestAuthEntryPoint restAuthEntryPoint;

    @BeforeEach
    void passThroughSecurityFilters() throws Exception {
        Answer<Void> pass = inv -> {
            ServletRequest req = inv.getArgument(0);
            ServletResponse res = inv.getArgument(1);
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(req, res);
            return null;
        };
        doAnswer(pass).when(jwtFilter).doFilter(any(), any(), any());
        doAnswer(pass).when(authRateLimitFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            var response = (jakarta.servlet.http.HttpServletResponse) inv.getArgument(1);
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return null;
        }).when(restAuthEntryPoint).commence(any(), any(), any());

        reset(authService);
    }

    private static UserDto user(long id, String username) {
        UserDto u = new UserDto();
        try {
            UserDto.class.getMethod("setId", Long.class).invoke(u, id);
            UserDto.class.getMethod("setUsername", String.class).invoke(u, username);
        } catch (Exception ignored) {}
        return u;
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {
        @Test
        @DisplayName("401 без аутентификации")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("200 при аутентификации")
        void ok() throws Exception {
            when(authService.me()).thenReturn(user(7L, "user@example.com"));

            mockMvc.perform(get("/api/auth/me")
                            .with(SecurityTestUtils.user())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.username").value("user@example.com"));

            verify(authService).me();
            verifyNoMoreInteractions(authService);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {
        @Test
        @DisplayName("200 при валидном теле")
        void ok() throws Exception {
            when(authService.login("user@example.com", "UltraStrong_Passw0rd#2024"))
                    .thenReturn("jwt-token");

            String body = """
              {"username":"user@example.com","password":"UltraStrong_Passw0rd#2024"}
            """;

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.accessToken").value("jwt-token"));

            verify(authService).login("user@example.com", "UltraStrong_Passw0rd#2024");
            verifyNoMoreInteractions(authService);
        }

        @Test
        @DisplayName("400 при пустых полях")
        void badRequest() throws Exception {
            String body = """
              {"username":"","password":""}
            """;

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {
        @Test
        @DisplayName("201 при валидном теле")
        void ok() throws Exception {
            when(authService.register("user@example.com", "UltraStrong_Passw0rd#2024"))
                    .thenReturn(user(1L, "user@example.com"));

            String body = """
              {
                "username":"user@example.com",
                "password":"UltraStrong_Passw0rd#2024",
                "confirmPassword":"UltraStrong_Passw0rd#2024"
              }
            """;

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("user@example.com"));

            verify(authService).register("user@example.com", "UltraStrong_Passw0rd#2024");
            verifyNoMoreInteractions(authService);
        }

        @Test
        @DisplayName("400 при пустых полях")
        void badRequest() throws Exception {
            String body = """
              {"username":"","password":""}
            """;

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties p = new CorsProperties();
            p.setEnabled(false);
            return p;
        }
    }
}
