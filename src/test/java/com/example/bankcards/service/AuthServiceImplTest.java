package com.example.bankcards.service;

import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.mapper.UserMapper;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.security.jwt.JwtProvider;
import com.example.bankcards.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtProvider jwtProvider;
    @Mock private UserService userService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("register: создаёт пользователя с ROLE_USER")
    void register_ok() {
        UserDto dto = new UserDto();
        dto.setId(10L);
        dto.setUsername("newUser");

        when(userService.createUser("newUser", "Secret123!", Set.of(Role.ROLE_USER)))
                .thenReturn(dto);

        UserDto out = authService.register("newUser", "Secret123!");

        assertThat(out.getId()).isEqualTo(10L);
        assertThat(out.getUsername()).isEqualTo("newUser");

        verify(userService).createUser("newUser", "Secret123!", Set.of(Role.ROLE_USER));
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(authenticationManager, jwtProvider);
    }

    @Test
    @DisplayName("me: возвращает UserDto из SecurityContext")
    void me_ok() {
        User entity = new User();
        entity.setId(7L);
        entity.setUsername("john_doe");

        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUser()).thenReturn(entity);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        UserDto dto = new UserDto();
        dto.setId(7L);
        dto.setUsername("john_doe");

        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(entity)).thenReturn(dto);

            UserDto out = authService.me();

            assertThat(out.getId()).isEqualTo(7L);
            assertThat(out.getUsername()).isEqualTo("john_doe");

            mapper.verify(() -> UserMapper.toDto(entity));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("login: успешная аутентификация и JWT")
    void login_ok() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(42L);
        when(principal.getUsername()).thenReturn("user123");

        doReturn(java.util.Set.of(Role.ROLE_USER))
                .when(principal).getAuthorities();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);

        when(authenticationManager.authenticate(tokenCaptor.capture()))
                .thenReturn(authentication);

        when(jwtProvider.generateToken(eq(42L), eq("user123"), any()))
                .thenReturn("jwt-token");

        String result = authService.login("  user123  ", "Secret123!");

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("jwt-token");
        UsernamePasswordAuthenticationToken passed = tokenCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(passed.getPrincipal()).isEqualTo("user123");
        org.assertj.core.api.Assertions.assertThat(passed.getCredentials()).isEqualTo("Secret123!");

        verify(authenticationManager).authenticate(any());
        verify(jwtProvider).generateToken(eq(42L), eq("user123"), any());
        verifyNoMoreInteractions(authenticationManager, jwtProvider);
        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("me: AccessDeniedException если нет аутентификации")
    void me_noAuth() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> authService.me())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("me: AccessDeniedException если principal не CustomUserDetails")
    void me_wrongPrincipalType() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("wrong");

        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        try {
            assertThatThrownBy(() -> authService.me())
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
