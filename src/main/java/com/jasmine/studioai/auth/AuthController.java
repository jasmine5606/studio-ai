package com.jasmine.studioai.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/status")
    public Status status() {
        return new Status(authService.enabled());
    }

    @PostMapping("/login")
    public AuthService.LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = bearerToken(authorization);
        authService.logout(token);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = bearerToken(authorization);
        AuthUser user = authService.authenticate(token);
        if (user == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return new MeResponse(user.username(), user.role());
    }

    private static String bearerToken(String header) {
        if (header == null) return "";
        String v = header.trim();
        if (v.toLowerCase().startsWith("bearer ")) {
            return v.substring(7).trim();
        }
        return v;
    }

    public record Status(boolean enabled) {
    }

    public record MeResponse(String username, String role) {
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}

