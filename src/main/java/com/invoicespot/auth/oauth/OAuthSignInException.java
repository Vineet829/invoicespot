package com.invoicespot.auth.oauth;

// Why a sign-in with a provider was refused, as the code the sign-in page
// explains.
public class OAuthSignInException extends RuntimeException {

    private final String code;

    public OAuthSignInException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
