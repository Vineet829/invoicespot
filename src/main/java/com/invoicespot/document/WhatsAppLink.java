package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.tax.TaxRegime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// A wa.me link that opens WhatsApp with a message written, to the client's
// number when it can be told: written with a + or 00 in front, or ten digits
// for a business in India. Otherwise WhatsApp asks whom to send it to.
final class WhatsAppLink {

    private WhatsAppLink() {}

    static String of(String phone, boolean indian, String text) {
        String number = number(phone, indian);
        String message = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        return number == null ? "https://wa.me/?text=" + message : "https://wa.me/" + number + "?text=" + message;
    }

    // The number in the international form wa.me takes, digits without a +.
    static String number(String phone, boolean indian) {
        if (phone == null) {
            return null;
        }
        String written = phone.trim();
        String digits = written.replaceAll("\\D", "");
        if (written.startsWith("+")) {
            return international(digits);
        }
        if (digits.startsWith("00")) {
            return international(digits.substring(2));
        }
        return indian && digits.length() == 10 ? "91" + digits : null;
    }

    // A business in India by its country, or by charging GST.
    static boolean inIndia(Business business, TaxRegime regime) {
        return "India".equalsIgnoreCase(Objects.requireNonNullElse(business.getCountry(), "").trim())
                || regime == TaxRegime.GST_INDIA;
    }

    // A country code and number run from 8 to 15 digits.
    private static String international(String digits) {
        return digits.length() >= 8 && digits.length() <= 15 ? digits : null;
    }
}
