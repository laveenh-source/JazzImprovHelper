package dev.laveenh.jazzanalyzer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Standard browser-hardening headers on every response, plus a strict content policy for the web page itself:
 * scripts may only come from this site (the sheet-music library is bundled with it, not loaded from a CDN).
 * (The API docs page needs inline scripts, so the policy skips the documentation paths.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String PAGE_POLICY = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "connect-src 'self'; "
            + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        String path = request.getRequestURI();
        boolean isDocs = path.startsWith("/swagger-ui") || path.startsWith("/v3/");
        boolean isApi = path.startsWith("/api/") || path.startsWith("/actuator");
        if (!isDocs && !isApi) {
            response.setHeader("Content-Security-Policy", PAGE_POLICY);
        }
        chain.doFilter(request, response);
    }
}
