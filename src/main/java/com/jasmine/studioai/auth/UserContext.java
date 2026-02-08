package com.jasmine.studioai.auth;

public final class UserContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static String username() {
        AuthUser u = HOLDER.get();
        return u == null ? "" : u.username();
    }

    public static String userId() {
        AuthUser u = HOLDER.get();
        return u == null ? "" : u.userId();
    }

    public static String role() {
        AuthUser u = HOLDER.get();
        return u == null ? "" : u.role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

