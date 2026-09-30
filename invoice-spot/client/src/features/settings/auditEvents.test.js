import { describe, expect, it } from "vitest";
import { eventLabel, whenLabel } from "./auditEvents";

describe("audit events", () => {
  it("names each kind of entry, and shows an unknown one as it is", () => {
    expect(eventLabel("SIGN_IN_FAILED")).toBe("Sign-in failed");
    expect(eventLabel("TWO_FACTOR_ENABLED")).toBe(
      "Two-factor sign-in turned on",
    );
    expect(eventLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("gives the day and time, or a dash", () => {
    expect(whenLabel("2026-09-30T14:05:00")).toBe("Sep 30, 2026, 2:05 PM");
    expect(whenLabel("not a time")).toBe("—");
  });
});
