import { expect, it } from "vitest";
import { numberPreview } from "./numbering";

it("previews the next number the way the server formats it", () => {
  expect(numberPreview("INV-", "1")).toBe("INV-0001");
  expect(numberPreview("2026/INV-", 100)).toBe("2026/INV-0100");
  expect(numberPreview("", "12345")).toBe("12345");
  expect(numberPreview("QUO-", "")).toBe("QUO-0001");
});
