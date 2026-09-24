package com.buruna.identity.web;

import com.buruna.identity.application.account.AccountService;
import com.buruna.identity.application.authentication.AuthenticationService;
import com.buruna.identity.domain.InvalidTokenException;
import com.buruna.identity.domain.User;
import com.buruna.shared.config.AppProperties;
import com.buruna.shared.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * Path restrito a /auth (SameSite=Strict): o navegador só envia o cookie em
     * requisições de primeira parte para esses endpoints, então um token CSRF
     * dedicado seria redundante aqui (ADR-41).
     */
    private static final String REFRESH_COOKIE_NAME = "buruna_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthenticationService authenticationService;
    private final AccountService accountService;
    private final ClientIpResolver clientIpResolver;
    private final AppProperties appProperties;

    public AuthController(AuthenticationService authenticationService, AccountService accountService,
                          ClientIpResolver clientIpResolver, AppProperties appProperties) {
        this.authenticationService = authenticationService;
        this.accountService = accountService;
        this.clientIpResolver = clientIpResolver;
        this.appProperties = appProperties;
    }

    private ResponseCookie refreshCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(appProperties.auth().cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(appProperties.jwt().refreshTokenExpiration())
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appProperties.auth().cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
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
        LoginResponse response = authenticationService.login(request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (response.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString());
        }
        return builder.body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }
        TokenResponse response = authenticationService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            authenticationService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
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
        LoginResponse response = authenticationService.authenticate2FA(request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (response.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString());
        }
        return builder.body(response);
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
