package com.buruna.identity;

import com.buruna.auth.domain.PasswordResetToken;
import com.buruna.auth.domain.RefreshToken;
import com.buruna.auth.repository.PasswordResetTokenRepository;
import com.buruna.auth.repository.RefreshTokenRepository;
import com.buruna.shared.notification.EmailSender;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rede de segurança do Epic 3 (identity = fusão auth+user): reproduz em JUnit os
 * cenários do test-phase2.sh (register/login/admin/refresh/logout/delete) e amplia
 * com rotação de refresh token, 2FA TOTP e reset de senha.
 *
 * Vive em com.buruna.identity já antecipando a migração [3.2]; por ora importa das
 * packages auth/user (os imports são ajustados quando o código de produção migra).
 *
 * O teste de "refresh token antigo morre" reflete o COMPORTAMENTO REAL do código
 * (TokenService.validateAndRotateRefreshToken faz rotação: deleta o token usado e
 * emite um novo), não a documentação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
@Import(IdentityIntegrationTest.TestConfig.class)
class IdentityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Emails são @Async e externos: substituídos por um no-op para hermeticidade. */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        EmailSender fakeEmailSender() {
            return (to, subject, body) -> { };
        }
    }

    private static final String KNOWN_PASSWORD = "Password@123";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User admin;
    User activeUser;
    User pendingUser;
    User inactiveUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        admin = userRepository.save(buildUser("admin@id.test", "idAdmin", Role.ADMIN, UserStatus.ACTIVE));
        activeUser = userRepository.save(buildUser("active@id.test", "idActive", Role.READER, UserStatus.ACTIVE));
        pendingUser = userRepository.save(buildUser("pending@id.test", "idPending", Role.READER, UserStatus.PENDING));
        inactiveUser = userRepository.save(buildUser("inactive@id.test", "idInactive", Role.READER, UserStatus.INACTIVE));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        ));
    }

    User buildUser(String email, String username, Role role, UserStatus status) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(KNOWN_PASSWORD));
        u.setPresentationMessage("test");
        u.setRole(role);
        u.setStatus(status);
        u.setQuotaGb(new BigDecimal("2.00"));
        u.setTotpEnabled(false);
        return u;
    }

    /** IP único por chamada para que cenários funcionais nunca disparem rate-limit. */
    static String uniqueIp() {
        int n = IP_SEQ.incrementAndGet();
        return String.format("10.%d.%d.%d", (n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF);
    }

    JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    String registerJson(String email, String username) {
        return """
                {"email":"%s","username":"%s","password":"%s","presentationMessage":"oi","captchaToken":"dummy"}
                """.formatted(email, username, KNOWN_PASSWORD);
    }

    MvcResult register(String json) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(JSON).content(json))
                .andReturn();
    }

    MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn();
    }

    /** Faz login de um usuário ACTIVE sem 2FA e devolve o refresh token emitido. */
    String loginAndGetRefreshToken(String email) throws Exception {
        MvcResult result = login(email, KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result).get("refreshToken").asText();
    }

    MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                        .contentType(JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andReturn();
    }

    String currentTotpCode(String secret) throws Exception {
        CodeGenerator generator = new DefaultCodeGenerator();
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        return generator.generate(secret, counter);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Registro — POST /auth/register
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void register_validPayload_returns201_andPersistsPendingReader() throws Exception {
        MvcResult result = register(registerJson("novo@id.test", "novoUser"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        User created = userRepository.findByEmail("novo@id.test").orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(created.getRole()).isEqualTo(Role.READER);
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        MvcResult result = register(registerJson("active@id.test", "outroUsername"));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        MvcResult result = register(registerJson("outro@id.test", "idActive"));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        MvcResult result = register(registerJson("nao-eh-email", "validUsername"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String json = """
                {"email":"curto@id.test","username":"curtoUser","password":"123","presentationMessage":"oi","captchaToken":"dummy"}""";
        MvcResult result = register(json);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void register_missingCaptcha_returns400() throws Exception {
        String json = """
                {"email":"semcaptcha@id.test","username":"semCaptcha","password":"%s","presentationMessage":"oi"}"""
                .formatted(KNOWN_PASSWORD);
        MvcResult result = register(json);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Login — POST /auth/login
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void login_activeUser_returns200_withTokens() throws Exception {
        MvcResult result = login("active@id.test", KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void login_pendingUser_returns403() throws Exception {
        MvcResult result = login("pending@id.test", KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void login_inactiveUser_returns403() throws Exception {
        MvcResult result = login("inactive@id.test", KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        MvcResult result = login("active@id.test", "WrongPassword!");
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void login_nonexistentEmail_returns401() throws Exception {
        MvcResult result = login("ninguem@id.test", KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void login_with2FAEnabled_returns200_requires2FA_withoutAccessToken() throws Exception {
        enableTotp(activeUser);

        MvcResult result = login("active@id.test", KNOWN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.get("requires2FA").asBoolean()).isTrue();
        assertThat(body.get("tempToken").asText()).isNotBlank();
        assertThat(body.has("accessToken")).isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Refresh — POST /auth/refresh  (rotação)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refresh_validToken_returns200_withNewTokens() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");

        MvcResult result = refresh(r1);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void refresh_rotates_oldTokenDies_newTokenWorks() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");

        // Rotação: R1 -> R2; R1 é deletado.
        MvcResult rotated = refresh(r1);
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        String r2 = body(rotated).get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);

        // Reusar R1 (antigo) deve falhar: já não existe.
        assertThat(refresh(r1).getResponse().getStatus()).isEqualTo(401);

        // R2 (novo) continua válido.
        assertThat(refresh(r2).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refresh_expiredToken_returns401() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");

        RefreshToken token = refreshTokenRepository.findByToken(r1).orElseThrow();
        token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(token);

        assertThat(refresh(r1).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        assertThat(refresh("nao-existe-este-token").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_userBecameInactive_returns403() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");

        activeUser.setStatus(UserStatus.INACTIVE);
        userRepository.save(activeUser);

        assertThat(refresh(r1).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void refresh_afterReLogin_previousTokenDies() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");
        // Novo login deleta o refresh anterior (um por usuário).
        loginAndGetRefreshToken("active@id.test");

        assertThat(refresh(r1).getResponse().getStatus()).isEqualTo(401);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Logout — POST /auth/logout
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void logout_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(JSON)
                        .content("{\"refreshToken\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_returns204_andInvalidatesRefreshToken() throws Exception {
        String r1 = loginAndGetRefreshToken("active@id.test");

        mockMvc.perform(post("/auth/logout")
                        .with(auth(activeUser))
                        .contentType(JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(r1)))
                .andExpect(status().isNoContent());

        assertThat(refresh(r1).getResponse().getStatus()).isEqualTo(401);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Delete account — DELETE /auth/account
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void deleteAccount_withoutAuth_returns401() throws Exception {
        mockMvc.perform(delete("/auth/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_returns204_andRemovesUserAndTokens() throws Exception {
        loginAndGetRefreshToken("active@id.test");

        mockMvc.perform(delete("/auth/account").with(auth(activeUser)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(activeUser.getId())).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Admin — /admin/users
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_list_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/admin/users").with(auth(activeUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_list_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/users").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void admin_listPending_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/users/pending").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void admin_getById_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", pendingUser.getId()).with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pendingUser.getId().toString()));
    }

    @Test
    void admin_getById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", UUID.randomUUID()).with(auth(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_approve_pendingUser_returns204_andActivates() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/approve", pendingUser.getId()).with(auth(admin)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(pendingUser.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void admin_approve_alreadyActive_returns409() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/approve", activeUser.getId()).with(auth(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_approve_nonexistent_returns404() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/approve", UUID.randomUUID()).with(auth(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_reject_pendingUser_returns204_andDeletes() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/reject", pendingUser.getId())
                        .with(auth(admin))
                        .contentType(JSON)
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(pendingUser.getId())).isEmpty();
    }

    @Test
    void admin_updateRole_returns200() throws Exception {
        mockMvc.perform(patch("/admin/users/{id}/role", activeUser.getId())
                        .with(auth(admin))
                        .contentType(JSON)
                        .content("{\"role\":\"COLLABORATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COLLABORATOR"));
    }

    @Test
    void admin_updateStatus_returns200() throws Exception {
        mockMvc.perform(patch("/admin/users/{id}/status", activeUser.getId())
                        .with(auth(admin))
                        .contentType(JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void admin_updateQuota_returns200() throws Exception {
        mockMvc.perform(patch("/admin/users/{id}/quota", activeUser.getId())
                        .with(auth(admin))
                        .contentType(JSON)
                        .content("{\"quotaGb\":10.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotaGb").value(10.5));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2FA — /auth/2fa/**
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void twoFA_setup_returns200_withSecretAndQrUri() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/2fa/setup").with(auth(activeUser)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = body(result);
        assertThat(body.get("secret").asText()).isNotBlank();
        assertThat(body.get("qrUri").asText()).isNotBlank();
    }

    @Test
    void twoFA_verify_validCode_enables2FA() throws Exception {
        MvcResult setup = mockMvc.perform(post("/auth/2fa/setup").with(auth(activeUser)))
                .andExpect(status().isOk())
                .andReturn();
        String secret = body(setup).get("secret").asText();

        mockMvc.perform(post("/auth/2fa/verify")
                        .with(auth(activeUser))
                        .contentType(JSON)
                        .content("""
                                {"code":"%s"}""".formatted(currentTotpCode(secret))))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(activeUser.getId()).orElseThrow().isTotpEnabled()).isTrue();
    }

    @Test
    void twoFA_verify_invalidCode_returns401() throws Exception {
        mockMvc.perform(post("/auth/2fa/setup").with(auth(activeUser)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/2fa/verify")
                        .with(auth(activeUser))
                        .contentType(JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoFA_status_reflectsEnabledState() throws Exception {
        mockMvc.perform(get("/auth/2fa/status").with(auth(activeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(false));

        enableTotp(activeUser);

        mockMvc.perform(get("/auth/2fa/status").with(auth(activeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true));
    }

    @Test
    void twoFA_authenticate_validCode_returns200_withTokens() throws Exception {
        String secret = enableTotp(activeUser);

        MvcResult loginResult = login("active@id.test", KNOWN_PASSWORD);
        String tempToken = body(loginResult).get("tempToken").asText();

        MvcResult result = mockMvc.perform(post("/auth/2fa/authenticate")
                        .contentType(JSON)
                        .content("""
                                {"tempToken":"%s","totpCode":"%s"}""".formatted(tempToken, currentTotpCode(secret))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(result).get("accessToken").asText()).isNotBlank();
    }

    @Test
    void twoFA_authenticate_invalidCode_returns401() throws Exception {
        enableTotp(activeUser);

        MvcResult loginResult = login("active@id.test", KNOWN_PASSWORD);
        String tempToken = body(loginResult).get("tempToken").asText();

        mockMvc.perform(post("/auth/2fa/authenticate")
                        .contentType(JSON)
                        .content("""
                                {"tempToken":"%s","totpCode":"000000"}""".formatted(tempToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoFA_disable_validCode_disables2FA() throws Exception {
        String secret = enableTotp(activeUser);

        mockMvc.perform(post("/auth/2fa/disable")
                        .with(auth(activeUser))
                        .contentType(JSON)
                        .content("""
                                {"code":"%s"}""".formatted(currentTotpCode(secret))))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(activeUser.getId()).orElseThrow().isTotpEnabled()).isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Password reset — /auth/password/**
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void forgot_activeUser_returns200_andCreatesToken() throws Exception {
        mockMvc.perform(post("/auth/password/forgot")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(JSON)
                        .content("{\"email\":\"active@id.test\"}"))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void forgot_nonexistentEmail_returns200_noToken() throws Exception {
        mockMvc.perform(post("/auth/password/forgot")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(JSON)
                        .content("{\"email\":\"ninguem@id.test\"}"))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void forgot_inactiveUser_returns200_noToken() throws Exception {
        mockMvc.perform(post("/auth/password/forgot")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(JSON)
                        .content("{\"email\":\"inactive@id.test\"}"))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetInfo_validToken_returns200_totpNotRequired() throws Exception {
        String token = createResetToken(activeUser);

        mockMvc.perform(get("/auth/password/reset-info").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpRequired").value(false));
    }

    @Test
    void resetInfo_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/password/reset-info").param("token", "nao-existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetInfo_usedToken_returns401() throws Exception {
        String token = createResetToken(activeUser);
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token).orElseThrow();
        prt.setUsedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(get("/auth/password/reset-info").param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetInfo_expiredToken_returns401() throws Exception {
        String token = createResetToken(activeUser);
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token).orElseThrow();
        prt.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(get("/auth/password/reset-info").param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_validToken_changesPassword_andInvalidatesTokens() throws Exception {
        loginAndGetRefreshToken("active@id.test");
        String token = createResetToken(activeUser);

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"NovaSenha@123"}""".formatted(token)))
                .andExpect(status().isOk());

        // Refresh tokens do usuário foram invalidados.
        assertThat(refreshTokenRepository.findAll()).isEmpty();
        // Nova senha funciona.
        assertThat(login("active@id.test", "NovaSenha@123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resetPassword_usedToken_returns401() throws Exception {
        String token = createResetToken(activeUser);
        mockMvc.perform(post("/auth/password/reset")
                .contentType(JSON)
                .content("""
                        {"token":"%s","newPassword":"NovaSenha@123"}""".formatted(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"OutraSenha@123"}""".formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_expiredToken_returns401() throws Exception {
        String token = createResetToken(activeUser);
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token).orElseThrow();
        prt.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"NovaSenha@123"}""".formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_2faEnabled_missingCode_returns401() throws Exception {
        enableTotp(activeUser);
        String token = createResetToken(activeUser);

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"NovaSenha@123"}""".formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_2faEnabled_validCode_returns200() throws Exception {
        String secret = enableTotp(activeUser);
        String token = createResetToken(activeUser);

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"NovaSenha@123","totpCode":"%s"}"""
                                .formatted(token, currentTotpCode(secret))))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Rate limit — POST /auth/register
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void register_rateLimit_sixthFromSameIp_returns429() throws Exception {
        String pinnedIp = "192.0.2.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/register")
                            .header("X-Forwarded-For", pinnedIp)
                            .contentType(JSON)
                            .content(registerJson("rl" + i + "@id.test", "rlUser" + i)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/auth/register")
                        .header("X-Forwarded-For", pinnedIp)
                        .contentType(JSON)
                        .content(registerJson("rl5@id.test", "rlUser5")))
                .andExpect(status().isTooManyRequests());
    }

    // ── helpers de domínio ──────────────────────────────────────────────────

    /** Habilita 2FA diretamente no banco e devolve o secret usado. */
    String enableTotp(User user) {
        String secret = new DefaultSecretGenerator().generate();
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        persisted.setTotpSecret(secret);
        persisted.setTotpEnabled(true);
        userRepository.save(persisted);
        return secret;
    }

    /** Cria um token de reset de senha válido para o usuário e devolve o valor bruto. */
    String createResetToken(User user) {
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(persisted);
        prt.setToken(UUID.randomUUID().toString());
        prt.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return passwordResetTokenRepository.save(prt).getToken();
    }
}
