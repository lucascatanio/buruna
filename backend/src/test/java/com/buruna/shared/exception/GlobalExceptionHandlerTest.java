package com.buruna.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (sem contexto Spring): o handler é instanciado com `new` e
 * a request é mockada. Prova que DomainException resolve para o status HTTP
 * correto via DomainErrorType (ADR-33) e que exceções do Spring MVC com status 4xx
 * mantêm esse status em vez de virar 500.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    /** Subclasse concreta só para o teste, simulando o que cada contexto criará. */
    private static final class SampleConflictException extends DomainException {
        private SampleConflictException() {
            super(DomainErrorType.CONFLICT, "recurso já existe");
        }
    }

    @Test
    void shouldMapDomainErrorTypeToHttpStatus_whenPureDomainExceptionThrown() {
        when(request.getRequestURI()).thenReturn("/mangas");

        ResponseEntity<ErrorResponse> response =
                handler.handleDomainException(new SampleConflictException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("recurso já existe");
        assertThat(response.getBody().path()).isEqualTo("/mangas");
    }

    @Test
    void shouldReturn404_whenSpringReportsMissingResource() {
        when(request.getRequestURI()).thenReturn("/api/swagger-ui.html");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new NoResourceFoundException(HttpMethod.GET, "swagger-ui.html"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void shouldKeepSpringStatus_whenFrameworkExceptionCarriesClientError() {
        when(request.getRequestURI()).thenReturn("/mangas");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new HttpRequestMethodNotSupportedException("PATCH"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldReturn500_whenUnexpectedExceptionThrown() {
        when(request.getRequestURI()).thenReturn("/mangas");

        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Ocorreu um erro inesperado");
    }
}
