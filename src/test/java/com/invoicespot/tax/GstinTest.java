package com.invoicespot.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GstinTest {

    @ParameterizedTest
    @ValueSource(strings = {"27AAPFU0939F1ZV", "29AAGCB7383J1Z4"})
    @DisplayName("a GSTIN with a known state and the right check character is valid")
    void valid(String gstin) {
        assertThat(Gstin.isValid(gstin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "27AAPFU0939F1ZW", // wrong check character
        "27AAPFU0939F1Z", // too short
        "99AAPFU0939F1ZV", // no such state
        "27aapfu0939f1zv", // not upper case yet
        "27AAPFU0939F1Z-",
    })
    @DisplayName("anything else is not")
    void invalid(String gstin) {
        assertThat(Gstin.isValid(gstin)).isFalse();
    }

    @Test
    @DisplayName("the check character is worked out over the first fourteen characters")
    void checkCharacter() {
        assertThat(Gstin.checkCharacter("27AAPFU0939F1Z")).isEqualTo('V');
        assertThat(Gstin.checkCharacter("29AAGCB7383J1Z")).isEqualTo('4');
    }

    @Test
    @DisplayName("parsing trims and upper-cases, keeps nothing as nothing, and names the format when wrong")
    void parse() {
        assertThat(Gstin.parse(" 27aapfu0939f1zv ")).isEqualTo("27AAPFU0939F1ZV");
        assertThat(Gstin.parse("  ")).isNull();
        assertThatThrownBy(() -> Gstin.parse("27AAPFU0939F1ZW"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Enter a valid GSTIN, such as 27AAPFU0939F1ZV");
    }

    @Test
    @DisplayName("the state is the first two characters, and the PAN the next ten")
    void parts() {
        assertThat(Gstin.stateCode("27AAPFU0939F1ZV")).isEqualTo("27");
        assertThat(Gstin.pan("27AAPFU0939F1ZV")).isEqualTo("AAPFU0939F");
    }

    @Test
    @DisplayName("states are named, territories without a legislature marked, and outside India is a place")
    void states() {
        assertThat(GstStates.name("27")).isEqualTo("Maharashtra");
        assertThat(GstStates.name("96")).isEqualTo("Outside India");
        assertThat(GstStates.isState("96")).isFalse();
        assertThat(GstStates.isPlace("96")).isTrue();
        assertThat(GstStates.isUnionTerritoryWithoutLegislature("04")).isTrue();
        assertThat(GstStates.isUnionTerritoryWithoutLegislature("07")).isFalse();
        assertThat(GstStates.places()).last().extracting(GstStates.State::code).isEqualTo("96");
    }
}
