import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

window.IS_REACT_ACT_ENVIRONMENT = true;

const encode = (value) =>
  btoa(JSON.stringify(value))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
const token = `${encode({ alg: "HS256" })}.${encode({
  sub: "u1",
  roles: ["User"],
  exp: 4102444800,
})}.sig`;

beforeEach(() => {
  localStorage.clear();
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response("{}", {
          status: 404,
          headers: { "content-type": "application/json" },
        }),
    ),
  );
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

// The app as index.jsx draws it, opened at `path`, with every path it shows.
const mount = async (path) => {
  const { Provider } = await import("react-redux");
  const { MemoryRouter, Routes, Route, useLocation } =
    await import("react-router-dom");
  const { store } = await import("./app/store");
  const { default: App } = await import("./App");
  const seen = [];
  const Probe = () => {
    seen.push(useLocation().pathname);
    return null;
  };
  const container = document.createElement("div");
  await act(async () => {
    createRoot(container).render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <Probe />
          <Routes>
            <Route path="/*" element={<App />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
  });
  return { store, container, seen };
};

it("draws nothing and does not bounce to /login while restoring", async () => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));
  const { store, container, seen } = await mount("/settings/security");
  expect(store.getState().auth.restoring).toBe(true);
  expect(container.innerHTML).toBe("");
  expect(seen.at(-1)).toBe("/settings/security");
  const { logIn } = await import("./features/auth/authSlice");
  await act(async () => {
    store.dispatch(logIn({ username: "asha", accessToken: token }));
  });
  expect(seen.at(-1)).toBe("/settings/security");
  expect(container.innerHTML).not.toBe("");
});

it("sends a failed restore to /login, as a signed-out visitor", async () => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));
  const { store, seen } = await mount("/settings/security");
  const { restoreFailed } = await import("./features/auth/authSlice");
  await act(async () => {
    store.dispatch(restoreFailed());
  });
  expect(seen.at(-1)).toBe("/login");
});
