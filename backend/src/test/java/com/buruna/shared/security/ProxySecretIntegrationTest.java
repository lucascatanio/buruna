package com.buruna.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com {@code app.proxy.secret} definido, só passa quem manda X-Proxy-Secret (ADR-43). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestPropertySource(properties = "app.proxy.secret=segredo-de-teste")
@Testcontainers
class ProxySecretIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn403_whenHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/auth/password/reset-info").param("token", "nao-existe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldReachController_whenHeaderMatches() throws Exception {
        // 401 é a resposta do próprio controller a um token de reset inexistente: a
        // requisição passou pelo filtro (sem o header seria 403)
        mockMvc.perform(get("/auth/password/reset-info").param("token", "nao-existe")
                        .header("X-Proxy-Secret", "segredo-de-teste"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn200WithoutHeader_whenPathIsHealth() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }
}
