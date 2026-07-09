package com.buruna;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test de contexto: sobe a aplicação completa contra um PostgreSQL real
 * (Testcontainers). Valida implicitamente que:
 *  - todas as migrations Flyway (V1..V20) aplicam num banco limpo;
 *  - o schema resultante bate com as entidades JPA (ddl-auto: validate);
 *  - todos os beans do contexto montam.
 *
 * Profile "local" seleciona LocalStorageClient e exclui GcsConfig (sem credenciais GCS).
 * Profile "test" fornece os placeholders dummy (ver application-test.yml).
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers
class SmokeApplicationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Test
    void contextLoads() {
        // Sucesso = contexto subiu, Flyway migrou e Hibernate validou o schema.
    }
}
