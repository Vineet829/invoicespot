package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhatsAppLinkTest {

    @Test
    @DisplayName("a number written with + or 00 is international, and ten digits are Indian for a business in India")
    void numbers() {
        assertThat(WhatsAppLink.number("+91 98765 43210", false)).isEqualTo("919876543210");
        assertThat(WhatsAppLink.number("0091-98765-43210", false)).isEqualTo("919876543210");
        assertThat(WhatsAppLink.number("+1 (415) 555-0100", true)).isEqualTo("14155550100");
        assertThat(WhatsAppLink.number("98765 43210", true)).isEqualTo("919876543210");
        assertThat(WhatsAppLink.number("98765 43210", false)).isNull();
        assertThat(WhatsAppLink.number("+12", false)).isNull();
        assertThat(WhatsAppLink.number("+1234567890123456", false)).isNull();
        assertThat(WhatsAppLink.number(" ", true)).isNull();
        assertThat(WhatsAppLink.number(null, true)).isNull();
    }

    @Test
    @DisplayName("the message is encoded for a link, and without a number WhatsApp asks whom to send it to")
    void links() {
        assertThat(WhatsAppLink.of("+91 98765 43210", false, "Hello Ada & Co, here is Invoice INV-1: https://x/p/a+b"))
                .isEqualTo("https://wa.me/919876543210?text=Hello%20Ada%20%26%20Co%2C%20here%20is%20Invoice%20INV-1"
                        + "%3A%20https%3A%2F%2Fx%2Fp%2Fa%2Bb");
        assertThat(WhatsAppLink.of("555 0100", false, "Hi")).isEqualTo("https://wa.me/?text=Hi");
    }
}
