package com.buruna.auth.controller;

import com.buruna.auth.dto.*;
import com.buruna.auth.service.AuthService;
import com.buruna.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user) {
        authService.deleteAccount(user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<Map<String, Boolean>> get2FAStatus(@AuthenticationPrincipal User user) {
        boolean enabled = authService.is2FAEnabled(user.getId());
        return ResponseEntity.ok(Map.of("totpEnabled", enabled));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpSetupResponse> setup2FA(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.setup2FA(user.getId()));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Void> verify2FA(@AuthenticationPrincipal User user,
                                          @Valid @RequestBody TotpCodeRequest request) {
        authService.verify2FA(user.getId(), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disable2FA(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody TotpCodeRequest request) {
        authService.disable2FA(user.getId(), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/authenticate")
    public ResponseEntity<LoginResponse> authenticate2FA(@Valid @RequestBody TotpAuthenticateRequest request) {
        return ResponseEntity.ok(authService.authenticate2FA(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/password/reset-info")
    public ResponseEntity<Map<String, Boolean>> resetInfo(@RequestParam String token) {
        boolean totpRequired = authService.isResetTokenTotpRequired(token);
        return ResponseEntity.ok(Map.of("totpRequired", totpRequired));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
