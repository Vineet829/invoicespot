import { expect, it } from "vitest";
import {
  gstinCheckCharacter,
  gstinProblem,
  isValidGstin,
  looksLikeGstin,
  normalizeGstin,
} from "./gstin";

it("accepts GSTINs whose check character is right, in any case and with spaces around", () => {
  expect(isValidGstin("27AAPFU0939F1ZV")).toBe(true);
  expect(isValidGstin(" 29aagcb7383j1z4 ")).toBe(true);
  expect(gstinCheckCharacter("27AAPFU0939F1Z")).toBe("V");
});

it("refuses a wrong check character, and anything not shaped like a GSTIN", () => {
  expect(isValidGstin("27AAPFU0939F1ZW")).toBe(false);
  expect(isValidGstin("27AAPFU0939F1Z")).toBe(false);
  expect(isValidGstin("")).toBe(false);
  expect(looksLikeGstin("27AAPFU0939F1ZW")).toBe(true);
  expect(looksLikeGstin("DE123456789")).toBe(false);
});

it("normalizes what was typed", () => {
  expect(normalizeGstin(" 27aapfu0939f1zv ")).toBe("27AAPFU0939F1ZV");
  expect(normalizeGstin(undefined)).toBe("");
});

it("says what is wrong once it can tell, and nothing while it may still be right", () => {
  expect(gstinProblem("")).toBeNull();
  expect(gstinProblem("27AAPF")).toBeNull();
  expect(gstinProblem("27AAPFU0939F1ZV")).toBeNull();
  expect(gstinProblem("A7")).toBe(
    "A GSTIN starts with the two-digit state code.",
  );
  expect(gstinProblem("27AA-")).toBe("A GSTIN has only letters and digits.");
  expect(gstinProblem("27AAPFU0939F1ZW")).toMatch(/last character/);
});
