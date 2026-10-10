import { afterEach, beforeEach, expect, it, vi } from "vitest";

// A reload through the app's own store and RTK Query, with only fetch faked.
beforeEach(() => {
  localStorage.clear();
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const reload = async (respond) => {
  const sent = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      sent.push(request);
      return respond(request);
    }),
  );
  const { store } = await import("../../app/store");
  const { authApiSlice } = await import("./authApiSlice");
  const { restoreSession } = await import("./authSlice");
  const before = store.getState().auth;
  await restoreSession(store, () =>
    store.dispatch(
      authApiSlice.endpoints.refreshSession.initiate(undefined, {
        track: false,
      }),
    ),
  );
  return { before, after: store.getState().auth, sent, store };
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

it("gets a reload's token back with one keepalive refresh, and keeps it only in memory", async () => {
  localStorage.setItem(
    "user",
    JSON.stringify({ username: "asha", accessToken: "old.jwt.x" }),
  );

  const { before, after, sent, store } = await reload(async () =>
    json({ success: true, username: "asha", accessToken: "fresh" }),
  );

  expect(before).toEqual({ user: null, restoring: true });
  expect(after).toEqual({
    user: { success: true, username: "asha", accessToken: "fresh" },
    restoring: false,
  });
  expect(sent).toHaveLength(1);
  expect(sent[0].url).toMatch(/\/auth\/new_access_token$/);
  expect(sent[0].keepalive).toBe(true);
  expect(sent[0].credentials).toBe("include");
  expect(sent[0].headers.get("authorization")).toBeNull();
  expect(localStorage.getItem("user")).not.toContain("fresh");
  expect(localStorage.getItem("user")).not.toContain("old.jwt.x");
  expect(store.getState().api.mutations).toEqual({});
});

it("signs out when the server has no session for the cookie", async () => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));

  const { after } = await reload(async () => new Response("", { status: 401 }));

  expect(after).toEqual({ user: null, restoring: false });
  expect(localStorage.getItem("user")).toBeNull();
});

it("keeps who was signed in for the next load when the server can't be reached", async () => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));

  const { after } = await reload(async () => {
    throw new TypeError("Failed to fetch");
  });

  expect(after).toEqual({ user: null, restoring: false });
  expect(JSON.parse(localStorage.getItem("user"))).toEqual({
    username: "asha",
  });
});

it("asks for nothing when no one was signed in", async () => {
  const { before, sent } = await reload(async () => json({}, 401));

  expect(before).toEqual({ user: null, restoring: false });
  expect(sent).toHaveLength(0);
});
