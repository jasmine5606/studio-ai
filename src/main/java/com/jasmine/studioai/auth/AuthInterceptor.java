package com.jasmine.studioai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authService = authService;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!authProperties.isEnabled()) {
            return true;
        }
        if (authProperties.getUsers() == null || authProperties.getUsers().isEmpty()) {
            return true;
        }

        String token = bearerToken(request.getHeader("Authorization"));
        AuthUser user = authService.authenticate(token);
        if (user == null) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorBody(401, "请先登录"));
    }

    private static String bearerToken(String header) {
        if (header == null) return "";
        String v = header.trim();
        if (v.toLowerCase().startsWith("bearer ")) {
            return v.substring(7).trim();
        }
        return v;
    }

    public record ErrorBody(int status, String message) {
    }
}

