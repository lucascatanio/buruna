package com.buruna.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (sem contexto Spring): o handler é instanciado com `new` e
 * a request é mockada. Prova que a base pura DomainException e a legada coexistem
 * e resolvem para o status HTTP correto (ADR-33).
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
    void shouldUseProvidedStatus_whenLegacyDomainExceptionThrown() {
        when(request.getRequestURI()).thenReturn("/x");

        ResponseEntity<ErrorResponse> response = handler.handleDomain(
                new LegacyHttpDomainException(HttpStatus.NOT_FOUND, "sumiu"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("sumiu");
    }
}
