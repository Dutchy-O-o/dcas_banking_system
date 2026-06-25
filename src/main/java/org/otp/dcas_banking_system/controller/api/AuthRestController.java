package org.otp.dcas_banking_system.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.LoginRequest;
import org.otp.dcas_banking_system.dto.LoginResponse;
import org.otp.dcas_banking_system.dto.RegisterRequest;
import org.otp.dcas_banking_system.security.JwtService;
import org.otp.dcas_banking_system.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("REST login attempt: {}", req.email());
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        String token = jwtService.generateToken(auth.getName());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        if (authService.isEmailRegistered(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }
        AuthService.RegisterResult result = authService.register(
                req.fullName(), req.email(), req.password(), req.tsw(), req.apw());
        return ResponseEntity.ok(Map.of(
                "message", "Registration successful",
                "otpAuthUrl", result.otpAuthUrl(),
                "secret", result.secret()
        ));
    }
}
