import { afterEach, describe, expect, it, vi } from "vitest";
import { hideSetup, setupHidden } from "./setupHidden";

describe("hiding the setup steps", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("keeps them hidden in the business they were hidden in", () => {
    expect(setupHidden("b-1")).toBe(false);
    hideSetup("b-1");
    expect(setupHidden("b-1")).toBe(true);
    expect(setupHidden("b-2")).toBe(false);
    expect(localStorage.getItem("setupHidden:b-1")).toBe("1");
  });

  it("shows them when storage can't be read, and doesn't fail when it can't be written", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("Storage is off");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("Storage is full");
    });
    expect(() => hideSetup("b-1")).not.toThrow();
    expect(setupHidden("b-1")).toBe(false);
  });
});
