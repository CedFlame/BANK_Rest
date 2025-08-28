package com.example.bankcards.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtParser jwtParser;
    private final UserDetailsService userDetailsService;

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        AntPathMatcher matcher = new AntPathMatcher();
        for (String skip : SWAGGER_WHITELIST) {
            if (matcher.match(skip, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        try {
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtParser.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtParser.isValid(token, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.debug("JWT invalid or expired");
                }
            }
            chain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            unauthorized(response, "Token expired");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT parse/validation error: {}", ex.getMessage());
            unauthorized(response, "Invalid token");
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + message + "\"}");
        }
    }
}
