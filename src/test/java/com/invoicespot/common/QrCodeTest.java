package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeTest {

    @Test
    @DisplayName("a QR code is a PNG that reads back as exactly the text put in")
    void readsBack() {
        String text = "upi://pay?pa=ada@okhdfcbank&pn=Ada%20%26%20Co&am=1500.00&cu=INR&tn=Invoice%20INV-0042";

        String image = QrCode.dataUri(text);

        assertThat(image).startsWith("data:image/png;base64,");
        assertThat(QrText.ofDataUri(image)).isEqualTo(text);
    }
}
