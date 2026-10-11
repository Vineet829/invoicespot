import { describe, expect, it } from "vitest";
import { returnPath } from "./returnPath";

describe("returnPath", () => {
  it("keeps the page's query and fragment", () => {
    expect(
      returnPath({
        pathname: "/documents",
        search: "?status=overdue&page=2",
        hash: "#top",
      }),
    ).toBe("/documents?status=overdue&page=2#top");
    expect(returnPath({ pathname: "/customers" })).toBe("/customers");
  });

  it("leads nowhere without a page", () => {
    expect(returnPath(undefined)).toBeNull();
    expect(returnPath({})).toBeNull();
  });
});
