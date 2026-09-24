package com.buruna.identity.web;

import com.buruna.identity.application.account.AccountService;
import com.buruna.identity.application.authentication.AuthenticationService;
import com.buruna.identity.domain.User;
import com.buruna.shared.security.ClientIpResolver;
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

    private final AuthenticationService authenticationService;
    private final AccountService accountService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthenticationService authenticationService, AccountService accountService,
                          ClientIpResolver clientIpResolver) {
        this.authenticationService = authenticationService;
        this.accountService = accountService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        accountService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user) {
        accountService.deleteAccount(user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<Map<String, Boolean>> get2FAStatus(@AuthenticationPrincipal User user) {
        boolean enabled = accountService.is2FAEnabled(user.getId());
        return ResponseEntity.ok(Map.of("totpEnabled", enabled));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpSetupResponse> setup2FA(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accountService.setup2FA(user.getId()));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Void> verify2FA(@AuthenticationPrincipal User user,
                                          @Valid @RequestBody TotpCodeRequest request) {
        accountService.verify2FA(user.getId(), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disable2FA(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody TotpCodeRequest request) {
        accountService.disable2FA(user.getId(), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/2fa/authenticate")
    public ResponseEntity<LoginResponse> authenticate2FA(@Valid @RequestBody TotpAuthenticateRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate2FA(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/password/reset-info")
    public ResponseEntity<Map<String, Boolean>> resetInfo(@RequestParam String token) {
        boolean totpRequired = accountService.isResetTokenTotpRequired(token);
        return ResponseEntity.ok(Map.of("totpRequired", totpRequired));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
