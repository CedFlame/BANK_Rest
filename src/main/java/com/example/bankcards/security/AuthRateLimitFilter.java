package com.example.bankcards.security;

import com.example.bankcards.config.properties.AuthRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimitProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // swagger whitelist
    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Value(staticConstructor = "of")
    private static class Bucket {
        int remaining;
        long resetEpochSec;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;

        String path = request.getServletPath();

        // 🔥 если swagger/doc путь → не фильтруем
        for (String skip : SWAGGER_WHITELIST) {
            if (matcher.match(skip, path)) {
                log.debug("Skipping rate-limit filter for {}", path);
                return true;
            }
        }

        // остальное решается по props.getPaths()
        return props.getPaths().stream().noneMatch(p -> matcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String key = clientKey(req);
        long nowSec = Instant.now().getEpochSecond();

        Bucket b = buckets.compute(key, (k, old) -> {
            if (old == null || nowSec >= old.resetEpochSec) {
                return Bucket.of(props.getCapacity(), nowSec + props.getWindowSeconds());
            }
            if (old.remaining <= 0) return old;
            return Bucket.of(old.remaining - 1, old.resetEpochSec);
        });

        if (b.remaining < 0) {
            long retryAfter = Math.max(0, b.resetEpochSec - nowSec);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too Many Requests\"}");
            log.warn("Rate limited {} (key={})", req.getServletPath(), key);
            return;
        }

        chain.doFilter(req, res);
    }

    private String clientKey(HttpServletRequest req) {
        String ip = (req.getHeader("X-Forwarded-For") != null)
                ? req.getHeader("X-Forwarded-For").split(",")[0].trim()
                : req.getRemoteAddr();
        return ip + "|" + req.getServletPath();
    }
}
