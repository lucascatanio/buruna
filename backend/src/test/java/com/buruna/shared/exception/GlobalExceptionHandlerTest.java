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
 * a request é mockada. Prova que DomainException resolve para o status HTTP
 * correto via DomainErrorType (ADR-33).
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
}
