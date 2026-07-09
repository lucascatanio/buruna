package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void of_validEmail_keepsValue() {
        assertThat(Email.of("user@example.com").value()).isEqualTo("user@example.com");
    }

    @Test
    void of_withoutAtSign_throwsValidation() {
        assertThatThrownBy(() -> Email.of("nao-eh-email"))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void of_withoutDomainDot_throwsValidation() {
        assertThatThrownBy(() -> Email.of("user@localhost"))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void of_null_throwsValidation() {
        assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void of_blank_throwsValidation() {
        assertThatThrownBy(() -> Email.of("   "))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void equality_isByValue() {
        assertThat(Email.of("a@b.co")).isEqualTo(Email.of("a@b.co"));
        assertThat(Email.of("a@b.co")).isNotEqualTo(Email.of("c@d.co"));
    }
}
