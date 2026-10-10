package com.invoicespot.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    private static final Map<String, String> VALUES =
            Map.of("client", "Morrow & Co.", "document", "Invoice INV-0042", "business", "Ada Studio");

    @Test
    @DisplayName("each placeholder is filled wherever it appears")
    void fills() {
        String message = "Hello {client},\n\nPlease find {document} from {business}.\n\n{business}";

        assertThat(Placeholders.fill(message, VALUES))
                .isEqualTo("Hello Morrow & Co.,\n\nPlease find Invoice INV-0042 from Ada Studio.\n\nAda Studio");
    }

    @Test
    @DisplayName("a value that looks like a placeholder, or holds $ or \\, is sent as it is")
    void valuesStayAsTheyAre() {
        Map<String, String> lookAlike = Map.of("client", "{business}", "business", "Ada");

        assertThat(Placeholders.fill("Hello {client}, from {business}", lookAlike))
                .isEqualTo("Hello {business}, from Ada");
        assertThat(Placeholders.fill("Hello {client}", Map.of("client", "Cash $1 \\ Co")))
                .isEqualTo("Hello Cash $1 \\ Co");
    }

    @Test
    @DisplayName("a placeholder without a value, or not written as one, is left as typed")
    void leavesTheRest() {
        assertThat(Placeholders.fill("Due {due}, {Client}, { client }, {client", VALUES))
                .isEqualTo("Due {due}, {Client}, { client }, {client");
    }

    @Test
    @DisplayName("a line whose placeholders all came out empty is left out, with the blank lines around it")
    void dropsEmptyLines() {
        String message = "Hello {client},\n\nView it online: {link}\n\n{payment}\n\n{business}";
        Map<String, String> linked = Map.of("client", "Ada", "link", "https://x", "payment", "", "business", "Bo");

        assertThat(Placeholders.fill(message, Map.of("client", "Ada", "link", "", "payment", "", "business", "Bo")))
                .isEqualTo("Hello Ada,\n\nBo");
        assertThat(Placeholders.fill(message, linked)).isEqualTo("Hello Ada,\n\nView it online: https://x\n\nBo");
        assertThat(Placeholders.fill("{link} or {business}", Map.of("link", "", "business", "Bo")))
                .isEqualTo(" or Bo");
        assertThat(Placeholders.fill("\nHi\n\n\n{due}", Map.of())).isEqualTo("\nHi\n\n\n{due}");
    }
}
