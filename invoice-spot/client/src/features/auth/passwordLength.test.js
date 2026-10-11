import { describe, expect, it } from "vitest";
import { fitsPassword } from "./passwordLength";

describe("fitsPassword", () => {
  it("counts bytes, as the server does, not characters", () => {
    const devanagari = String.fromCodePoint(0x0915);
    expect(fitsPassword("a".repeat(72))).toBe(true);
    expect(fitsPassword("a".repeat(73))).toBe(false);
    expect(fitsPassword(devanagari.repeat(24))).toBe(true);
    expect(fitsPassword(devanagari.repeat(25))).toBe(false);
    expect(fitsPassword(undefined)).toBe(true);
  });
});
