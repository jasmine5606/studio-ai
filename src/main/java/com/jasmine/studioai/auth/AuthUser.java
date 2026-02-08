package com.jasmine.studioai.auth;

import java.util.Objects;

public final class AuthUser {
    private final String userId;
    private final String username;
    private final String role;

    public AuthUser(String username, String role) {
        this(null, username, role);
    }

    public AuthUser(String userId, String username, String role) {
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.role = role == null || role.isBlank() ? "USER" : role;
    }

    public String userId() { return userId != null ? userId : username; }
    public String username() { return username; }
    public String role() { return role; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthUser u)) return false;
        return username.equals(u.username) && role.equals(u.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, role);
    }

    @Override
    public String toString() {
        return "AuthUser[userId=" + userId + ", username=" + username + ", role=" + role + "]";
    }
}

