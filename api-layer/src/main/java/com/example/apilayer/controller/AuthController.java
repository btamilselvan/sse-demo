package com.example.apilayer.controller;

import com.example.apilayer.dto.AuthDtos.ErrorResponse;
import com.example.apilayer.dto.AuthDtos.LoginRequest;
import com.example.apilayer.dto.AuthDtos.LoginResponse;
import com.example.apilayer.dto.AuthDtos.MeResponse;
import com.example.apilayer.security.JwtService;
import com.example.apilayer.security.UserStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserStore userStore;
    private final JwtService jwtService;

    public AuthController(UserStore userStore, JwtService jwtService) {
        this.userStore = userStore;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        if (!userStore.isValid(request.username(), request.password())) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid username or password"));
        }
        String token = jwtService.generateToken(request.username());
        return ResponseEntity.ok(new LoginResponse(token, request.username()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
        }
        return ResponseEntity.ok(new MeResponse(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless auth -- nothing to invalidate server-side. This endpoint
        // exists purely so the frontend has a clean call to make before
        // dropping its in-memory token.
        return ResponseEntity.ok().build();
    }
}
