package com.buruna.shared.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O cliente HTTP das fontes externas não segue redirect: a URL é conferida antes da chamada, e
 * um redirect levaria a requisição para um endereço que ninguém conferiu.
 */
class ExternalSourceConfigTest {

    private HttpServer server;
    private final AtomicBoolean targetHit = new AtomicBoolean();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/origem", exchange -> {
            exchange.getResponseHeaders().add("Location", "/destino");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/destino", exchange -> {
            targetHit.set(true);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldReturnRedirectWithoutFollowing_whenServerAnswers302() {
        // Arrange
        RestTemplate restTemplate = ExternalSourceConfig.restTemplate(Duration.ofSeconds(2), Duration.ofSeconds(2));
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/origem";

        // Act
        ResponseEntity<Void> response = restTemplate.getForEntity(url, Void.class);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(targetHit).isFalse();
    }
}
