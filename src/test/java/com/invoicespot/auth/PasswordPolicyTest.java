package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
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
}
