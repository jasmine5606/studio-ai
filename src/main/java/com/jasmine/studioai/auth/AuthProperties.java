package com.jasmine.studioai.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * Enable app-level authentication for all /api/** endpoints (except /api/auth/**).
     */
    private boolean enabled = true;

    /**
     * Token TTL in hours.
     */
    private int tokenTtlHours = 24 * 7;

    private List<User> users = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(int tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users == null ? new ArrayList<>() : users;
    }

    public Optional<User> findUser(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return users.stream().filter(u -> username.equals(u.getUsername())).findFirst();
    }

    public static class User {
        private String username;
        private String password;
        private String role = "USER";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role == null || role.isBlank() ? "USER" : role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}

