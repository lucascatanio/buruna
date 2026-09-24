package com.buruna.identity.application.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 dos tokens de autenticação (refresh e reset de senha) antes de persistir.
 * Um vazamento do banco expõe só o hash, que não autentica nada sozinho — o valor em
 * claro só existe em trânsito: no cookie httpOnly (refresh) ou no link do e-mail
 * (reset de senha).
 */
public final class TokenHash {

    private TokenHash() {
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
