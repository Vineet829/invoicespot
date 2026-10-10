import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, it } from "vitest";

// Every dropdown is the styled control (Select.jsx), so no page draws a
// browser select of its own.
const pages = readdirSync(join(__dirname, "../.."), { recursive: true })
  .filter((file) => /\.jsx$/.test(file) && !/\.test\.jsx$/.test(file))
  .map((file) => join(__dirname, "../..", file));

it("leaves no browser-drawn select on any page", () => {
  expect(pages.length).toBeGreaterThan(50);
  const remaining = pages.filter((file) =>
    /<select[\s>]/.test(readFileSync(file, "utf8")),
  );
  expect(remaining).toEqual([]);
});
