package com.buruna.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernameTest {

    @Test
    void of_validUsername_keepsValue() {
        assertThat(Username.of("reader").value()).isEqualTo("reader");
    }

    @Test
    void of_minLength_isAccepted() {
        assertThat(Username.of("abc").value()).isEqualTo("abc");
    }

    @Test
    void of_maxLength_isAccepted() {
        String fifty = "a".repeat(50);
        assertThat(Username.of(fifty).value()).isEqualTo(fifty);
    }

    @Test
    void of_tooShort_throwsValidation() {
        assertThatThrownBy(() -> Username.of("ab"))
                .isInstanceOf(InvalidUsernameException.class);
    }

    @Test
    void of_tooLong_throwsValidation() {
        assertThatThrownBy(() -> Username.of("a".repeat(51)))
                .isInstanceOf(InvalidUsernameException.class);
    }

    @Test
    void of_null_throwsValidation() {
        assertThatThrownBy(() -> Username.of(null))
                .isInstanceOf(InvalidUsernameException.class);
    }
}
