package com.jasmine.studioai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final Map<String, String> localTokens = new ConcurrentHashMap<>();

    public LoginResponse login(String username, String password) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Auth disabled");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }

        var user = properties.findUser(username).orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (user.getPassword() == null || user.getPassword().isBlank() || !user.getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        localTokens.put(token, username);
        try {
            redis.opsForValue().set(tokenKey(token), username, tokenTtl());
        } catch (Exception ignored) {
        }

        return new LoginResponse(token, username, user.getRole());
    }

    public AuthUser authenticate(String token) {
        if (!properties.isEnabled()) {
            return new AuthUser("anonymous", "ADMIN");
        }
        if (token == null || token.isBlank()) return null;
        String username = null;
        try {
            username = redis.opsForValue().get(tokenKey(token));
        } catch (Exception ignored) {
        }
        if (username == null || username.isBlank()) {
            username = localTokens.get(token);
        }
        if (username == null || username.isBlank()) return null;

        var user = properties.findUser(username).orElse(null);
        if (user == null) return null;
        return new AuthUser(username, user.getRole());
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        localTokens.remove(token);
        try {
            redis.delete(tokenKey(token));
        } catch (Exception ignored) {
        }
    }

    public boolean enabled() {
        return properties.isEnabled() && properties.getUsers() != null && !properties.getUsers().isEmpty();
    }

    private Duration tokenTtl() {
        int hours = properties.getTokenTtlHours();
        if (hours <= 0) hours = 24 * 7;
        return Duration.ofHours(hours);
    }

    private static String tokenKey(String token) {
        return "auth:token:" + token;
    }

    public record LoginResponse(String token, String username, String role) {
    }
}
