package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Str0ng!pw", "Aa1!aaaa", "ÜberSecret9#"})
    @DisplayName("eight or more characters with upper and lowercase letters, a digit and a symbol pass")
    void strongPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Aa1!aaa", "aa1!aaaa", "AA1!AAAA", "Aa!!aaaa", "Aa11aaaa"})
    @DisplayName("short passwords, or ones missing any kind of character, fail")
    void weakPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isFalse();
    }

    @Test
    @DisplayName("the message names every kind of character a password needs, the number among them")
    void requirements() {
        assertThat(PasswordPolicy.REQUIREMENTS)
                .isEqualTo("Password must be at least 8 characters long, with at least 1 uppercase letter,"
                        + " 1 lowercase letter, 1 number and 1 symbol");
    }
}
