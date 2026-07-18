package com.creditai.controller;

import com.creditai.dto.ApiResponse;
import com.creditai.dto.AuthResponse;
import com.creditai.dto.LoginRequest;
import com.creditai.entity.User;
import com.creditai.repository.UserRepository;
import com.creditai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest req) {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
            String token = jwtUtil.generateToken(user.getUsername());

            return ResponseEntity.ok(ApiResponse.ok(
                    new AuthResponse(token, user.getUsername(), user.getFullName(), user.getRole().name())));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Credenciales inválidas"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@RequestBody UserRegisterRequest req) {
        if (userRepository.existsByUsername(req.username()))
            return ResponseEntity.badRequest().body(ApiResponse.error("Username ya existe"));
        User user = User.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .email(req.email())
                .role(User.Role.ANALYST)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Usuario registrado exitosamente"));
    }

    record UserRegisterRequest(String username, String password, String fullName, String email, String role) {}
}
