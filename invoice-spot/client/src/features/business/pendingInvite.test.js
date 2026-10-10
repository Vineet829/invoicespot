import { afterEach, describe, expect, it, vi } from "vitest";
import {
  forgetInvite,
  pendingInvitePath,
  rememberInvite,
} from "./pendingInvite";

describe("pendingInvite", () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it("leads back to the invitation opened before signing in", () => {
    expect(pendingInvitePath()).toBeNull();
    rememberInvite("abc_DEF-123");
    expect(pendingInvitePath()).toBe("/invite/abc_DEF-123");
    forgetInvite();
    expect(pendingInvitePath()).toBeNull();
  });

  it("stops leading back once the invitation could no longer be open", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-01T10:00:00Z"));
    rememberInvite("token");
    vi.setSystemTime(new Date("2026-09-08T09:59:00Z"));
    expect(pendingInvitePath()).toBe("/invite/token");
    vi.setSystemTime(new Date("2026-09-08T10:00:00Z"));
    expect(pendingInvitePath()).toBeNull();
  });

  it("ignores an entry it can't read", () => {
    localStorage.setItem("pendingInvite", "{not json");
    expect(pendingInvitePath()).toBeNull();
    localStorage.setItem("pendingInvite", JSON.stringify({ token: 42 }));
    expect(pendingInvitePath()).toBeNull();
  });
});
