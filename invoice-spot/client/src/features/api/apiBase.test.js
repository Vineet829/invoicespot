import { afterEach, beforeEach, expect, it, vi } from "vitest";

// apiBase reads the build's settings once, as it loads, so each case loads it
// afresh, as a production build would.
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("DEV", false);
  vi.stubEnv("PROD", true);
});
afterEach(() => {
  vi.unstubAllEnvs();
});

const apiBase = async () => (await import("./apiBase")).API_BASE;

it("calls the API on the app's own origin when the build names none", async () => {
  vi.stubEnv("VITE_API_URL", undefined);

  expect(await apiBase()).toBe("/api/v1");
});

it("calls the API the build names", async () => {
  vi.stubEnv("VITE_API_URL", "https://api.example.com/api/v1");

  expect(await apiBase()).toBe("https://api.example.com/api/v1");
});
