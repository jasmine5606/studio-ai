package com.jasmine.studioai.security.jwt;

import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.repository.ApiTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jwt.enabled", havingValue = "true")
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ApiTokenRepository apiTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.toLowerCase().startsWith("bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        try {
            var claims = jwtService.validateToken(token);
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            UserContext.set(new com.jasmine.studioai.auth.AuthUser(userId, username, role));
        } catch (Exception e) {
            response.setStatus(401);
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
