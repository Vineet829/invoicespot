import { expect, it } from "vitest";
import { HSN_SAC_PATTERN, isValidHsnSac, normalizeHsnSac } from "./hsnSac";

it("drops spaces and takes 4 to 8 digits, as the server does", () => {
  expect(normalizeHsnSac(" 9983 14 ")).toBe("998314");
  expect(normalizeHsnSac(undefined)).toBe("");
  for (const code of ["4901", "9983 14", "12345678"]) {
    expect(isValidHsnSac(code)).toBe(true);
  }
  for (const code of ["", "123", "123456789", "99A3", "9983-14"]) {
    expect(isValidHsnSac(code)).toBe(false);
  }
});

it("lets an input take exactly what the check takes", () => {
  const pattern = new RegExp(`^(?:${HSN_SAC_PATTERN})$`, "v");
  for (const code of [
    "4901",
    " 9983 14 ",
    "12345678",
    "123",
    "123456789",
    "99A3",
  ]) {
    expect(pattern.test(code)).toBe(isValidHsnSac(code));
  }
});
