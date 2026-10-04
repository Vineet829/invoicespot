import { describe, expect, it } from "vitest";
import { compactAmount, formatAmount } from "./money";

describe("money", () => {
  it("groups rupees in lakhs and crores, as the PDFs do", () => {
    expect(formatAmount(100000, "INR")).toBe("1,00,000.00");
    expect(formatAmount("12345678.9", "INR")).toBe("1,23,45,678.90");
    expect(formatAmount(-150000, "inr")).toBe("-1,50,000.00");
    expect(formatAmount(999, "INR")).toBe("999.00");
  });

  it("groups every other currency in thousands", () => {
    expect(formatAmount(100000, "USD")).toBe("100,000.00");
    expect(formatAmount(1234567.891, "EUR")).toBe("1,234,567.89");
    expect(formatAmount(100000)).toBe("100,000.00");
  });

  it("reads anything that isn't a number as zero", () => {
    expect(formatAmount(undefined, "INR")).toBe("0.00");
    expect(formatAmount("abc", "USD")).toBe("0.00");
  });

  it("gives the same answers when formats are used again", () => {
    expect([1, 2, 3].map((value) => formatAmount(value * 1000, "INR"))).toEqual(
      ["1,000.00", "2,000.00", "3,000.00"],
    );
    expect(formatAmount(1000, "USD")).toBe("1,000.00");
    expect(compactAmount(1500, "USD")).toBe("1.5K");
    expect(compactAmount(150000, "INR")).toBe("1.5L");
  });

  it("shortens axis amounts to lakhs and crores for rupees", () => {
    expect(compactAmount(120000, "INR")).toBe("1.2L");
    expect(compactAmount(12000000, "INR")).toBe("1.2Cr");
    expect(compactAmount(1500000, "USD")).toBe("1.5M");
    expect(compactAmount(1500, "USD")).toBe("1.5K");
  });
});
