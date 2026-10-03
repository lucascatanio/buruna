package com.buruna.shared.security;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Autentica os pushes do Pub/Sub: o endpoint é público para o Spring Security, e a prova
 * de origem é o token OIDC que o Pub/Sub assina em nome da conta de serviço da push
 * subscription (ADR-42).
 */
@Component
public class PubSubPushAuthenticator {

    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final TokenVerifier verifier;
    private final String serviceAccount;

    public PubSubPushAuthenticator(@Value("${app.pubsub.push-audience:}") String audience,
                                   @Value("${app.pubsub.push-service-account:}") String serviceAccount,
                                   Environment environment) {
        boolean configured = !audience.isBlank() && !serviceAccount.isBlank();
        if (!configured && !environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException(
                    "app.pubsub.push-audience e app.pubsub.push-service-account são obrigatórios fora do profile 'local'");
        }
        // no profile local não há Pub/Sub: sem configuração, todo push é recusado
        this.verifier = configured ? TokenVerifier.newBuilder().setAudience(audience).build() : null;
        this.serviceAccount = serviceAccount;
    }

    public void verify(String authorizationHeader) {
        if (verifier == null || authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Push do Pub/Sub sem token válido");
        }
        JsonWebSignature token;
        try {
            token = verifier.verify(authorizationHeader.substring(7));
        } catch (TokenVerifier.VerificationException | RuntimeException e) {
            // RuntimeException além de VerificationException: um token que não é sequer um
            // JWT bem formado (ex.: string arbitrária) faz o parser da biblioteca do Google
            // lançar IllegalArgumentException antes da verificação de assinatura começar.
            throw new BadCredentialsException("Push do Pub/Sub com token inválido");
        }
        JsonWebSignature.Payload payload = token.getPayload();
        if (!GOOGLE_ISSUERS.contains(payload.getIssuer())
                || !serviceAccount.equals(payload.get("email"))
                || !Boolean.TRUE.equals(payload.get("email_verified"))) {
            throw new BadCredentialsException("Push do Pub/Sub de origem não autorizada");
        }
    }
}
