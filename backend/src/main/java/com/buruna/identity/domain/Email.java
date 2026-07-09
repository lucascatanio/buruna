package com.buruna.identity.domain;

import java.util.regex.Pattern;

/**
 * Value Object imutável de e-mail. Valida formato na construção (ADR-34).
 * A entidade persiste a String; o VO é usado na borda da application
 * ({@code Email.of(request.email()).value()}), igual ao padrão de {@code Score}.
 */
public final class Email {

    private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final String value;

    private Email(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new InvalidEmailException(value);
        }
        this.value = value;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email e)) return false;
        return value.equals(e.value);
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
