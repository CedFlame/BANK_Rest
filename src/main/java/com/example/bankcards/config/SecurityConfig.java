package com.example.bankcards.config;

import com.example.bankcards.config.properties.CorsProperties;
import com.example.bankcards.security.AuthRateLimitFilter;
import com.example.bankcards.security.RestAuthEntryPoint;
import com.example.bankcards.security.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final CorsProperties corsProps;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AccessDeniedHandler accessDeniedHandler,
                                           Optional<CorsConfigurationSource> corsSourceOpt) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // --- Swagger & OpenAPI docs (всегда открыты) ---
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // --- Auth endpoints ---
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- Прочее ---
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .anyRequest().authenticated()
                )
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; " +
                                        "base-uri 'none'; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "img-src 'self' data:; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "script-src 'self'; " +
                                        "connect-src 'self'"
                        ))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentTypeOptions(Customizer.withDefaults())
                );

        // --- CORS ---
        if (corsSourceOpt.isPresent()) {
            http.cors(c -> c.configurationSource(corsSourceOpt.get()));
        } else {
            http.cors(c -> c.disable());
        }

        // --- Filters ---
        http.addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return authEntryPoint;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (req, res, ex) -> {
            res.setStatus(403);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Forbidden\"}");
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public Optional<CorsConfigurationSource> corsConfigurationSource() {
        if (!corsProps.isEnabled()) {
            return Optional.empty();
        }
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(corsProps.getAllowedOrigins());
        c.setAllowedMethods(corsProps.getAllowedMethods());
        c.setAllowedHeaders(corsProps.getAllowedHeaders());
        c.setExposedHeaders(corsProps.getExposedHeaders());
        c.setAllowCredentials(corsProps.isAllowCredentials());
        c.setMaxAge(Duration.ofSeconds(corsProps.getMaxAge()));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return Optional.of(source);
    }
}
