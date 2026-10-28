package com.example.apilayer.security;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hardcoded in-memory user store for this POC. Passwords are plaintext
 * on purpose -- there is no real credential store or database here, this
 * is local-dev-only.
 */
@Component
public class UserStore {

    private final Map<String, String> usernameToPassword = Map.of(
            "alice", "password123",
            "bob", "password123",
            "charlie", "password123"
    );

    public boolean isValid(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        String expected = usernameToPassword.get(username);
        return expected != null && expected.equals(password);
    }

    public boolean exists(String username) {
        return username != null && usernameToPassword.containsKey(username);
    }
}
