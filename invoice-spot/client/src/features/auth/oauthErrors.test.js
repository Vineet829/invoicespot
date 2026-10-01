import { describe, expect, it } from "vitest";
import { oauthErrorMessage } from "./oauthErrors";

describe("oauthErrorMessage", () => {
  it("explains each reason the server gives", () => {
    expect(oauthErrorMessage("cancelled")).toBe(
      "You cancelled signing in with Google.",
    );
    expect(oauthErrorMessage("expired")).toBe(
      "The sign-in took too long or was interrupted. Please try again.",
    );
    expect(oauthErrorMessage("unverified_email")).toBe(
      "Google hasn't verified that email address, so it can't be used to sign in.",
    );
    expect(oauthErrorMessage("demo_account")).toBe(
      "The demo account opens only with the Guest button.",
    );
    expect(oauthErrorMessage("deactivated")).toBe(
      "This account has been deactivated. Please contact us.",
    );
    expect(oauthErrorMessage("already_linked")).toBe(
      "This account is already linked to a different Google account.",
    );
    expect(oauthErrorMessage("failed")).toBe(
      "Signing in with Google didn't work. Please try again.",
    );
  });

  it("falls back to the general message for a code it doesn't know", () => {
    for (const code of ["something_new", "toString", "", undefined]) {
      expect(oauthErrorMessage(code)).toBe(
        "Signing in with Google didn't work. Please try again.",
      );
    }
  });

  it("names the provider it's given", () => {
    expect(oauthErrorMessage("cancelled", "GitHub")).toBe(
      "You cancelled signing in with GitHub.",
    );
    expect(oauthErrorMessage("failed", "GitHub")).toBe(
      "Signing in with GitHub didn't work. Please try again.",
    );
  });
});
