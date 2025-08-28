package com.example.bankcards.controller;

import com.example.bankcards.dto.LoginRequest;
import com.example.bankcards.dto.RegisterRequest;
import com.example.bankcards.testutil.Jsons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Smoke-тесты DTO сериализации для AuthController")
class AuthControllerDtoSmokeTest {

    @Test
    @DisplayName("LoginRequest сериализуется в JSON без ошибок")
    void loginRequest_toJson() {
        LoginRequest r = new LoginRequest();
        try {
            LoginRequest.class.getMethod("setUsername", String.class).invoke(r, "user@example.com");
            LoginRequest.class.getMethod("setPassword", String.class).invoke(r, "secret");
        } catch (Exception ignore) {}

        String json = Jsons.toJson(r);
        assertThat(json).contains("user@example.com");
        assertThat(json).contains("secret");
    }

    @Test
    @DisplayName("RegisterRequest сериализуется в JSON без ошибок")
    void registerRequest_toJson() {
        RegisterRequest r = new RegisterRequest();
        try {
            RegisterRequest.class.getMethod("setUsername", String.class).invoke(r, "new@example.com");
            RegisterRequest.class.getMethod("setPassword", String.class).invoke(r, "Secret123!");
        } catch (Exception ignore) {}

        String json = Jsons.toJson(r);
        assertThat(json).contains("new@example.com");
        assertThat(json).contains("Secret123!");
    }
}
