package com.example.bankcards.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_BEARER = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank Cards API")
                        .description("""
                        REST API для управления пользователями, картами и переводами.
                        Авторизация по JWT: нажмите Authorize и введите `Bearer <token>`.
                        """)
                        .version("1.0.0")
                        .contact(new Contact().name("Team").email("support@example.com"))
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_BEARER,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_BEARER)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER));
    }
}
