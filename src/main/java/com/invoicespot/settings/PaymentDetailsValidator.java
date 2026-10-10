package com.invoicespot.settings;

import com.invoicespot.common.ApiException;
import com.invoicespot.settings.dto.PaymentDetailsRequest;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

// Checks payment details as they are saved, so documents never show a code
// or ID a client can't pay to, and puts them in one standard form.
final class PaymentDetailsValidator {

    private static final Pattern UPI = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9.]{1,31}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[A-Za-z0-9 -]{4,34}$");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern SWIFT = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern IBAN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    private static final Pattern SORT_CODE = Pattern.compile("^(\\d{2})-?(\\d{2})-?(\\d{2})$");
    private static final Pattern ROUTING = Pattern.compile("^\\d{9}$");
    private static final Pattern BSB = Pattern.compile("^(\\d{3})-?(\\d{3})$");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private PaymentDetailsValidator() {}

    static PaymentDetails validate(PaymentDetailsRequest request) {
        PaymentDetails details = new PaymentDetails();
        details.setAccountHolder(trimmed(request.accountHolder()));
        details.setBankName(trimmed(request.bankName()));
        details.setInstructions(trimmed(request.instructions()));

        String accountNumber = trimmed(request.accountNumber());
        if (accountNumber != null && !ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw invalid("Enter the account number with letters, digits, spaces or hyphens, 4 to 34 characters");
        }
        details.setAccountNumber(accountNumber);

        String upiId = trimmed(request.upiId());
        if (upiId != null) {
            upiId = upiId.toLowerCase(Locale.ROOT);
            if (!UPI.matcher(upiId).matches()) {
                throw invalid("Enter a UPI ID such as yourname@okhdfcbank");
            }
        }
        details.setUpiId(upiId);

        String link = trimmed(request.payLink());
        if (link != null && !isHttps(link)) {
            throw invalid("Enter a payment link that starts with https://");
        }
        details.setLink(link);

        String code = trimmed(request.bankCode());
        if (code != null) {
            BankCodeType type = type(request.bankCodeType());
            details.setBankCodeType(type);
            details.setBankCode(bankCode(type, code));
            if (type == BankCodeType.OTHER) {
                String label = trimmed(request.bankCodeLabel());
                if (label == null) {
                    throw invalid("Name the bank code, such as Branch code");
                }
                details.setBankCodeLabel(label);
            }
        }
        return details;
    }

    private static String bankCode(BankCodeType type, String value) {
        String compact = value.replace(" ", "").toUpperCase(Locale.ROOT);
        return switch (type) {
            case IFSC -> matching(
                    IFSC, compact, "Enter an IFSC such as HDFC0001234: 4 letters, a zero, then 6 letters or digits");
            case SWIFT -> matching(
                    SWIFT, compact, "Enter a SWIFT / BIC code of 8 or 11 letters and digits, such as HDFCINBB");
            case IBAN -> {
                if (!IBAN.matcher(compact).matches() || !checksumMatches(compact)) {
                    throw invalid("Enter the IBAN as your bank gives it. This one's check digits don't match");
                }
                yield compact;
            }
            case SORT_CODE -> {
                Matcher digits = SORT_CODE.matcher(compact);
                if (!digits.matches()) {
                    throw invalid("Enter a 6-digit sort code, such as 12-34-56");
                }
                yield digits.group(1) + "-" + digits.group(2) + "-" + digits.group(3);
            }
            case ROUTING -> matching(ROUTING, compact, "Enter a 9-digit routing number");
            case BSB -> {
                Matcher digits = BSB.matcher(compact);
                if (!digits.matches()) {
                    throw invalid("Enter a 6-digit BSB, such as 062-000");
                }
                yield digits.group(1) + "-" + digits.group(2);
            }
            case OTHER -> value;
        };
    }

    // ISO 13616: with its first four characters moved to the end and letters
    // read as 10 to 35, a valid IBAN leaves 1 when divided by 97.
    private static boolean checksumMatches(String iban) {
        StringBuilder digits = new StringBuilder();
        for (char character : (iban.substring(4) + iban.substring(0, 4)).toCharArray()) {
            digits.append(Character.getNumericValue(character));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    private static boolean isHttps(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static BankCodeType type(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("Choose what kind of bank code this is");
        }
        try {
            return BankCodeType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid("Choose what kind of bank code this is");
        }
    }

    private static String matching(Pattern pattern, String value, String message) {
        if (!pattern.matcher(value).matches()) {
            throw invalid(message);
        }
        return value;
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
