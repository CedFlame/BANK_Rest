package com.example.bankcards.service;

import com.example.bankcards.config.properties.UsersProperties;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.UserDto;
import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.exception.BadRequestException;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.mapper.PageDtoMapper;
import com.example.bankcards.mapper.UserMapper;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UsersProperties usersProps;
    @Mock UserRepository userRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    UserServiceImpl userService;

    @Test
    @DisplayName("createUser: нормализация логина, кодирование пароля, роли из аргумента")
    void createUser_ok_withRoles() {
        when(passwordEncoder.encode("Secret123!")).thenReturn("ENC");
        when(userRepository.existsByUsername("john")).thenReturn(false);

        User saved = User.builder()
                .id(5L)
                .username("john")
                .password("ENC")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .roles(EnumSet.of(Role.ROLE_ADMIN))
                .build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserDto expected = new UserDto();
        expected.setId(5L);
        expected.setUsername("john");

        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(saved)).thenReturn(expected);

            UserDto out = userService.createUser("  John  ", "Secret123!", Set.of(Role.ROLE_ADMIN));

            assertThat(out.getId()).isEqualTo(5L);
            assertThat(out.getUsername()).isEqualTo("john");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User toSave = captor.getValue();
            assertThat(toSave.getUsername()).isEqualTo("john");
            assertThat(toSave.getPassword()).isEqualTo("ENC");
            assertThat(toSave.getRoles()).containsExactly(Role.ROLE_ADMIN);

            verify(userRepository).existsByUsername("john");
            verifyNoMoreInteractions(userRepository);
            mapper.verify(() -> UserMapper.toDto(saved));
        }
    }

    @Test
    @DisplayName("createUser: если роли не заданы — по умолчанию ROLE_USER")
    void createUser_ok_defaultRole() {
        when(passwordEncoder.encode("pwd")).thenReturn("E");
        when(userRepository.existsByUsername("user")).thenReturn(false);

        User saved = User.builder().id(1L).username("user").password("E")
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .roles(EnumSet.of(Role.ROLE_USER)).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserDto dto = new UserDto(); dto.setId(1L); dto.setUsername("user");
        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(saved)).thenReturn(dto);

            UserDto out = userService.createUser("user", "pwd", null);
            assertThat(out.getId()).isEqualTo(1L);
            assertThat(out.getUsername()).isEqualTo("user");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).containsExactly(Role.ROLE_USER);
        }
    }

    @Test
    @DisplayName("createUser: пустой username -> BadRequest")
    void createUser_blankUsername() {
        assertThatThrownBy(() -> userService.createUser("   ", "pwd", Set.of(Role.ROLE_USER)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("createUser: пустой пароль -> BadRequest")
    void createUser_blankPassword() {
        assertThatThrownBy(() -> userService.createUser("user", "   ", Set.of(Role.ROLE_USER)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("createUser: пользователь уже существует -> UserAlreadyExists")
    void createUser_exists() {
        when(userRepository.existsByUsername("user")).thenReturn(true);
        assertThatThrownBy(() -> userService.createUser("user", "pwd", Set.of(Role.ROLE_USER)))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository).existsByUsername("user");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("getById: найден -> маппинг в DTO")
    void getById_found() {
        User entity = User.builder().id(9L).username("u").roles(EnumSet.of(Role.ROLE_USER)).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(entity));

        UserDto dto = new UserDto(); dto.setId(9L); dto.setUsername("u");

        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(entity)).thenReturn(dto);

            Optional<UserDto> out = userService.getById(9L);
            assertThat(out).isPresent();
            assertThat(out.get().getId()).isEqualTo(9L);
        }
    }

    @Test
    @DisplayName("getById: null -> empty")
    void getById_null() {
        assertThat(userService.getById(null)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("getByUsername: нормализация и поиск")
    void getByUsername_found() {
        User entity = User.builder().id(2L).username("john").roles(EnumSet.of(Role.ROLE_USER)).build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(entity));

        UserDto dto = new UserDto(); dto.setId(2L); dto.setUsername("john");

        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(entity)).thenReturn(dto);

            Optional<UserDto> out = userService.getByUsername("  JoHn  ");
            assertThat(out).isPresent();
            assertThat(out.get().getUsername()).isEqualTo("john");
        }
    }

    @Test
    @DisplayName("getByUsername: пусто -> empty")
    void getByUsername_blank() {
        assertThat(userService.getByUsername("   ")).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("updateRoles: заменяет роли и сохраняет")
    void updateRoles_ok() {
        User entity = User.builder().id(3L).username("u3").roles(EnumSet.of(Role.ROLE_USER)).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(entity));

        User updated = User.builder().id(3L).username("u3").roles(EnumSet.of(Role.ROLE_ADMIN)).build();
        when(userRepository.save(any(User.class))).thenReturn(updated);

        UserDto dto = new UserDto(); dto.setId(3L); dto.setUsername("u3");
        try (MockedStatic<UserMapper> mapper = Mockito.mockStatic(UserMapper.class)) {
            mapper.when(() -> UserMapper.toDto(updated)).thenReturn(dto);

            UserDto out = userService.updateRoles(3L, Set.of(Role.ROLE_ADMIN));
            assertThat(out.getId()).isEqualTo(3L);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).containsExactly(Role.ROLE_ADMIN);
        }
    }

    @Test
    @DisplayName("updateRoles: null id -> BadRequest")
    void updateRoles_nullId() {
        assertThatThrownBy(() -> userService.updateRoles(null, Set.of(Role.ROLE_USER)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("updateRoles: не найден -> UserNotFound")
    void updateRoles_notFound() {
        when(userRepository.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateRoles(100L, Set.of(Role.ROLE_USER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("delete: удаляет при наличии")
    void delete_ok() {
        when(userRepository.existsById(7L)).thenReturn(true);
        userService.delete(7L);
        verify(userRepository).deleteById(7L);
    }

    @Test
    @DisplayName("delete: null id -> BadRequest")
    void delete_nullId() {
        assertThatThrownBy(() -> userService.delete(null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("delete: не найден -> UserNotFound")
    void delete_notFound() {
        when(userRepository.existsById(8L)).thenReturn(false);
        assertThatThrownBy(() -> userService.delete(8L)).isInstanceOf(UserNotFoundException.class);
        verify(userRepository).existsById(8L);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("list: без поиска -> findAll(pageable), сортировка по id DESC, размер из клампа")
    void list_noSearch() {
        when(usersProps.getDefaultPageSize()).thenReturn(20);
        when(usersProps.getMaxPageSize()).thenReturn(50);

        List<User> content = List.of(
                User.builder().id(3L).username("c").roles(EnumSet.of(Role.ROLE_USER)).build(),
                User.builder().id(1L).username("a").roles(EnumSet.of(Role.ROLE_USER)).build()
        );
        Page<User> page = new PageImpl<>(content, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")), 2);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        UserDto d1 = new UserDto(); d1.setId(3L); d1.setUsername("c");
        UserDto d2 = new UserDto(); d2.setId(1L); d2.setUsername("a");
        List<UserDto> mapped = List.of(d1, d2);
        PageDto<UserDto> pageDto = new PageDto<>(); // допустим пустой конструктор есть

        try (MockedStatic<UserMapper> um = Mockito.mockStatic(UserMapper.class);
             MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {

            um.when(() -> UserMapper.toDto(content.get(0))).thenReturn(d1);
            um.when(() -> UserMapper.toDto(content.get(1))).thenReturn(d2);
            pm.when(() -> PageDtoMapper.toPageDto(page, mapped)).thenReturn(pageDto);

            PageDto<UserDto> out = userService.list(0, 0, null);
            assertThat(out).isSameAs(pageDto);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findAll(captor.capture());
            Pageable used = captor.getValue();
            assertThat(used.getPageNumber()).isEqualTo(0);
            assertThat(used.getPageSize()).isEqualTo(20); // взят default, потому что size=0
            assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }

    @Test
    @DisplayName("list: с поиском -> findByUsernameContainingIgnoreCase")
    void list_withSearch() {
        when(usersProps.getDefaultPageSize()).thenReturn(10);
        when(usersProps.getMaxPageSize()).thenReturn(30);

        Page<User> page = new PageImpl<>(
                List.of(),
                PageRequest.of(1, 30, Sort.by(Sort.Direction.DESC, "id")),
                0
        );

        when(userRepository.findByUsernameContainingIgnoreCase(eq("Ann"), any(Pageable.class)))
                .thenReturn(page);

        PageDto<UserDto> pageDto = new PageDto<>();

        try (MockedStatic<PageDtoMapper> pm = Mockito.mockStatic(PageDtoMapper.class)) {
            pm.when(() -> PageDtoMapper.toPageDto(eq(page), anyList())).thenReturn(pageDto);

            PageDto<UserDto> out = userService.list(1, 100, "  Ann  ");
            assertThat(out).isSameAs(pageDto);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findByUsernameContainingIgnoreCase(eq("Ann"), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(30); // кламп к max
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        }
    }

    @Test
    @DisplayName("existsByUsername: нормализация и делегирование")
    void existsByUsername_ok() {
        when(userRepository.existsByUsername("kate")).thenReturn(true);
        assertThat(userService.existsByUsername("  KATE ")).isTrue();
        verify(userRepository).existsByUsername("kate");
    }

    @Test
    @DisplayName("existsByUsername: пусто -> false без похода в БД")
    void existsByUsername_blank() {
        assertThat(userService.existsByUsername("   ")).isFalse();
        verifyNoInteractions(userRepository);
    }
}
