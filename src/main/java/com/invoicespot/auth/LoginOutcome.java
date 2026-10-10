package com.invoicespot.auth;

// What a correct password leads to: a session, or a challenge that a
// two-factor code completes.
public sealed interface LoginOutcome permits TokenBundle, LoginOutcome.TwoFactorChallenge {

    record TwoFactorChallenge(String challenge) implements LoginOutcome {}
}
