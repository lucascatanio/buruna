package com.buruna.identity.domain;

/**
 * Value Object imutável de nome de usuário (3–50 caracteres). Valida na construção
 * (ADR-34); a entidade persiste a String. Usado na borda da application
 * ({@code Username.of(request.username()).value()}).
 */
public final class Username {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 50;

    private final String value;

    private Username(String value) {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidUsernameException(value);
        }
        this.value = value;
    }

    public static Username of(String value) {
        return new Username(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Username u)) return false;
        return value.equals(u.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
