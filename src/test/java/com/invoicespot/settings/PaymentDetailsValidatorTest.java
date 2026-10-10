package com.invoicespot.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.invoicespot.common.ApiException;
import com.invoicespot.settings.dto.PaymentDetailsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentDetailsValidatorTest {

    private static PaymentDetailsRequest code(String type, String code) {
        return new PaymentDetailsRequest(null, null, null, type, code, null, null, null, null);
    }

    private static String refusal(PaymentDetailsRequest request) {
        Throwable refused = catchThrowable(() -> PaymentDetailsValidator.validate(request));
        assertThat(refused).isInstanceOf(ApiException.class);
        return refused.getMessage();
    }

    @Test
    @DisplayName("details are trimmed, blank ones are left out, and a UPI ID is lower-cased")
    void normalises() {
        PaymentDetails details = PaymentDetailsValidator.validate(new PaymentDetailsRequest(
                "  Ada & Co ", " ", "5010 0123 4567", null, null, "ignored", " Ada.Co@OKHDFCBANK ",
                "https://pay.example.com/ada", "  "));

        assertThat(details.getAccountHolder()).isEqualTo("Ada & Co");
        assertThat(details.getBankName()).isNull();
        assertThat(details.getAccountNumber()).isEqualTo("5010 0123 4567");
        assertThat(details.getBankCodeType()).isNull();
        assertThat(details.getBankCodeLabel()).isNull();
        assertThat(details.getUpiId()).isEqualTo("ada.co@okhdfcbank");
        assertThat(details.getLink()).isEqualTo("https://pay.example.com/ada");
        assertThat(details.getInstructions()).isNull();
        assertThat(PaymentDetailsValidator.validate(code(null, null)).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("each kind of bank code is checked and written the standard way")
    void bankCodes() {
        assertThat(PaymentDetailsValidator.validate(code("IFSC", "hdfc0001234")).getBankCode())
                .isEqualTo("HDFC0001234");
        assertThat(PaymentDetailsValidator.validate(code("SWIFT", "hdfc inbb")).getBankCode()).isEqualTo("HDFCINBB");
        assertThat(PaymentDetailsValidator.validate(code("SWIFT", "DEUTDEFF500")).getBankCode())
                .isEqualTo("DEUTDEFF500");
        assertThat(PaymentDetailsValidator.validate(code("IBAN", "gb82 west 1234 5698 7654 32")).getBankCode())
                .isEqualTo("GB82WEST12345698765432");
        assertThat(PaymentDetailsValidator.validate(code("SORT_CODE", "123456")).getBankCode()).isEqualTo("12-34-56");
        assertThat(PaymentDetailsValidator.validate(code("ROUTING", "021000021")).getBankCode()).isEqualTo("021000021");
        assertThat(PaymentDetailsValidator.validate(code("BSB", "062000")).getBankCode()).isEqualTo("062-000");
        PaymentDetails other = PaymentDetailsValidator.validate(
                new PaymentDetailsRequest(null, null, null, "OTHER", "0042", " Branch code ", null, null, null));
        assertThat(other.getBankCode()).isEqualTo("0042");
        assertThat(other.bankCodeName()).isEqualTo("Branch code");
        assertThat(PaymentDetailsValidator.validate(code("IFSC", "HDFC0001234")).bankCodeName()).isEqualTo("IFSC");
    }

    @Test
    @DisplayName("a code that can't be right is refused with what it should look like")
    void refusesBadCodes() {
        assertThat(refusal(code("IFSC", "HDFC1001234"))).startsWith("Enter an IFSC such as HDFC0001234");
        assertThat(refusal(code("SWIFT", "HDFCIN"))).startsWith("Enter a SWIFT / BIC code");
        assertThat(refusal(code("IBAN", "GB82WEST12345698765433"))).contains("check digits don't match");
        assertThat(refusal(code("SORT_CODE", "12345"))).isEqualTo("Enter a 6-digit sort code, such as 12-34-56");
        assertThat(refusal(code("ROUTING", "02100002"))).isEqualTo("Enter a 9-digit routing number");
        assertThat(refusal(code("BSB", "06200"))).isEqualTo("Enter a 6-digit BSB, such as 062-000");
        assertThat(refusal(code(null, "HDFC0001234"))).isEqualTo("Choose what kind of bank code this is");
        assertThat(refusal(code("ABA", "021000021"))).isEqualTo("Choose what kind of bank code this is");
        assertThat(refusal(code("OTHER", "0042"))).isEqualTo("Name the bank code, such as Branch code");
    }

    @Test
    @DisplayName("an account number, UPI ID or payment link that can't be paid to is refused")
    void refusesBadDetails() {
        assertThat(refusal(new PaymentDetailsRequest(null, null, "12", null, null, null, null, null, null)))
                .startsWith("Enter the account number");
        assertThat(refusal(new PaymentDetailsRequest(null, null, "1234#5678", null, null, null, null, null, null)))
                .startsWith("Enter the account number");
        assertThat(refusal(new PaymentDetailsRequest(null, null, null, null, null, null, "ada", null, null)))
                .isEqualTo("Enter a UPI ID such as yourname@okhdfcbank");
        assertThat(refusal(new PaymentDetailsRequest(null, null, null, null, null, null, "ada@1bank", null, null)))
                .isEqualTo("Enter a UPI ID such as yourname@okhdfcbank");
        for (String link : new String[] {"http://pay.example.com", "https://", "pay.example.com", "javascript:x"}) {
            assertThat(refusal(new PaymentDetailsRequest(null, null, null, null, null, null, null, link, null)))
                    .isEqualTo("Enter a payment link that starts with https://");
        }
    }
}
