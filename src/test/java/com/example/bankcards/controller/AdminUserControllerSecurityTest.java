package com.example.bankcards.controller;

import com.example.bankcards.config.SecurityConfig;
import com.example.bankcards.config.properties.CorsProperties;
import com.example.bankcards.dto.CreateUserAdminRequest;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UpdateRolesRequest;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.GlobalExceptionHandler;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.security.AuthRateLimitFilter;
import com.example.bankcards.security.RestAuthEntryPoint;
import com.example.bankcards.security.jwt.JwtFilter;
import com.example.bankcards.service.UserService;
import com.example.bankcards.testutil.SecurityTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminUserController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminUserControllerSecurityTest.TestSecurityBeans.class})
class AdminUserControllerSecurityTest {

    @Resource MockMvc mockMvc;
    @Resource ObjectMapper objectMapper;

    @MockBean UserService userService;

    // фильтры и entry point, от которых зависит SecurityConfig
    @MockBean JwtFilter jwtFilter;
    @MockBean AuthRateLimitFilter authRateLimitFilter;
    @MockBean RestAuthEntryPoint restAuthEntryPoint;

    @BeforeEach
    void passThroughSecurityFilters() throws Exception {
        // Все кастомные фильтры — в режим "пропустить дальше"
        Answer<Void> pass = inv -> {
            ServletRequest req = inv.getArgument(0);
            ServletResponse res = inv.getArgument(1);
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(req, res);
            return null;
        };
        doAnswer(pass).when(jwtFilter).doFilter(any(), any(), any());
        doAnswer(pass).when(authRateLimitFilter).doFilter(any(), any(), any());

        // AuthenticationEntryPoint — всегда 401 для неаутентифицированных запросов
        doAnswer(inv -> {
            var response = (jakarta.servlet.http.HttpServletResponse) inv.getArgument(1);
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return null;
        }).when(restAuthEntryPoint).commence(any(), any(), any());

        reset(userService);
    }

    private static UserDto user(long id, String username) {
        UserDto u = new UserDto();
        try {
            UserDto.class.getMethod("setId", Long.class).invoke(u, id);
            UserDto.class.getMethod("setUsername", String.class).invoke(u, username);
        } catch (Exception ignored) {}
        return u;
    }

    // -------- list --------
    @Nested
    class ListUsers {
        @Test
        @DisplayName("GET /api/admin/users → 401 без аутентификации")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("GET /api/admin/users → 200 с ролью ADMIN и проксируются page/size/search")
        void ok_admin() throws Exception {
            when(userService.list(1, 20, "ann")).thenReturn(new PageDto<>());

            mockMvc.perform(get("/api/admin/users")
                            .with(SecurityTestUtils.admin())
                            .param("page", "1")
                            .param("size", "20")
                            .param("search", "ann"))
                    .andExpect(status().isOk());

            verify(userService).list(1, 20, "ann");
        }
    }

    // -------- getById --------
    @Nested
    class GetById {
        @Test
        @DisplayName("GET /api/admin/users/{id} → 401 без аутентификации")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/admin/users/{id}", 42))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("GET /api/admin/users/{id} → 200 когда найден")
        void found() throws Exception {
            when(userService.getById(42L)).thenReturn(Optional.of(new UserDto()));

            mockMvc.perform(get("/api/admin/users/{id}", 42).with(SecurityTestUtils.admin()))
                    .andExpect(status().isOk());

            verify(userService).getById(42L);
        }

        @Test
        @DisplayName("GET /api/admin/users/{id} → пробрасывает UserNotFoundException когда пусто")
        void notFound() throws Exception {
            when(userService.getById(404L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/admin/users/{id}", 404).with(SecurityTestUtils.admin()))
                    .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                            .isInstanceOf(UserNotFoundException.class));

            verify(userService).getById(404L);
        }
    }

    // -------- create --------
    @Nested
    class CreateUser {
        @Test
        @DisplayName("POST /api/admin/users → 401 без аутентификации")
        void unauthorized() throws Exception {
            String body = """
                {"email":"admin@example.com","password":"secret","roles":[]}
            """;
            mockMvc.perform(post("/api/admin/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("POST /api/admin/users: пустые/отсутствующие роли → по умолчанию ROLE_USER")
        void defaultRole() throws Exception {
            CreateUserAdminRequest req = new CreateUserAdminRequest();
            req.setEmail("admin@example.com");
            req.setPassword("secret");
            req.setRoles(Set.of()); // пусто

            when(userService.createUser(anyString(), anyString(), anySet())).thenReturn(new UserDto());

            mockMvc.perform(post("/api/admin/users")
                            .with(SecurityTestUtils.admin())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(userService).createUser(eq("admin@example.com"), eq("secret"),
                    eq(EnumSet.of(Role.ROLE_USER)));
        }

        @Test
        @DisplayName("POST /api/admin/users: переданные роли проксируются в сервис")
        void customRoles() throws Exception {
            CreateUserAdminRequest req = new CreateUserAdminRequest();
            req.setEmail("boss@example.com");
            req.setPassword("pwd");
            req.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));

            when(userService.createUser(anyString(), anyString(), anySet())).thenReturn(new UserDto());

            mockMvc.perform(post("/api/admin/users")
                            .with(SecurityTestUtils.admin())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(userService).createUser(eq("boss@example.com"), eq("pwd"),
                    eq(EnumSet.of(Role.ROLE_USER, Role.ROLE_ADMIN)));
        }
    }

    // -------- updateRoles --------
    @Nested
    class UpdateRoles {
        @Test
        @DisplayName("PATCH /api/admin/users/{id}/roles → 401 без аутентификации")
        void unauthorized() throws Exception {
            String body = """
                {"roles":["ROLE_ADMIN"]}
            """;
            mockMvc.perform(patch("/api/admin/users/{id}/roles", 7L)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("PATCH /api/admin/users/{id}/roles → 200 и вызывает service.updateRoles")
        void ok() throws Exception {
            UpdateRolesRequest req = new UpdateRolesRequest();
            req.setRoles(Set.of(Role.ROLE_ADMIN));

            when(userService.updateRoles(eq(7L), anySet())).thenReturn(new UserDto());

            mockMvc.perform(patch("/api/admin/users/{id}/roles", 7L)
                            .with(SecurityTestUtils.admin())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            verify(userService).updateRoles(7L, EnumSet.of(Role.ROLE_ADMIN));
        }
    }

    // -------- delete --------
    @Nested
    class DeleteUser {
        @Test
        @DisplayName("DELETE /api/admin/users/{id} → 401 без аутентификации")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/admin/users/{id}", 9L).with(csrf()))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("DELETE /api/admin/users/{id} → 204 и вызывает service.delete")
        void ok() throws Exception {
            mockMvc.perform(delete("/api/admin/users/{id}", 9L)
                            .with(SecurityTestUtils.admin())
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(userService).delete(9L);
        }
    }

    // -------- exists --------
    @Nested
    class Exists {
        @Test
        @DisplayName("GET /api/admin/users/exists → 401 без аутентификации")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/admin/users/exists").param("username", "a@b.com"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("GET /api/admin/users/exists?username=email → 200 и JSON {exists:true|false}")
        void ok() throws Exception {
            when(userService.existsByUsername("a@b.com")).thenReturn(true);

            mockMvc.perform(get("/api/admin/users/exists")
                            .with(SecurityTestUtils.admin())
                            .param("username", "a@b.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));

            verify(userService).existsByUsername("a@b.com");
        }

        @Test
        @DisplayName("GET /api/admin/users/exists: валидация email → 400, сервис не вызывается")
        void validationError() throws Exception {
            mockMvc.perform(get("/api/admin/users/exists")
                            .with(SecurityTestUtils.admin())
                            .param("username", "not-an-email"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }
    }

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties p = new CorsProperties();
            p.setEnabled(false); // в тестах CORS отключаем
            return p;
        }
    }
}
