package com.arena.cpj.auth;

import com.arena.cpj.user.User;

public final class UserContext {

    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(User user) {
        CURRENT.set(user);
    }

    public static User get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
