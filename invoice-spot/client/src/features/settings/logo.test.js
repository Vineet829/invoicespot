import { expect, it } from "vitest";
import { printsOnPdfs } from "./logo";

it("says a PNG, JPEG or GIF logo uploaded here prints on PDFs", () => {
  expect(printsOnPdfs("/uploads/3f2c.png")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.jpg")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.jpeg")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.gif")).toBe(true);
});

it("says a WebP from before, or a logo not uploaded here, is left off PDFs", () => {
  expect(printsOnPdfs("/uploads/3f2c.webp")).toBe(false);
  expect(printsOnPdfs("https://example.com/logo.png")).toBe(false);
  expect(printsOnPdfs("/uploads/3f2c.png?v=2")).toBe(false);
  expect(printsOnPdfs("/uploads/brand/3f2c.png")).toBe(false);
});

it("has nothing to warn about when there is no logo", () => {
  expect(printsOnPdfs("")).toBe(true);
  expect(printsOnPdfs("  ")).toBe(true);
  expect(printsOnPdfs(null)).toBe(true);
  expect(printsOnPdfs(undefined)).toBe(true);
});
