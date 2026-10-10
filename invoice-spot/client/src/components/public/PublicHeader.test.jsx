import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

// The header as a public page draws it, through the app's store, signed
// out. Nothing is fetched.
window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
let scrolled;
let scrolledTo;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  scrolled = vi.fn();
  Element.prototype.scrollIntoView = scrolled;
  scrolledTo = vi.fn();
  vi.stubGlobal("scrollTo", scrolledTo);
});
const unmount = () => {
  act(() => root.unmount());
  container.remove();
};
afterEach(() => {
  unmount();
  delete Element.prototype.scrollIntoView;
  delete document.fonts;
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

// The fonts as a browser reports them: a promise that settles once they
// have loaded.
const fontsLoading = () => {
  let loaded;
  const ready = new Promise((resolve) => {
    loaded = resolve;
  });
  Object.defineProperty(document, "fonts", {
    value: { ready },
    configurable: true,
  });
  return () => act(async () => loaded());
};

const SECTIONS = [
  { id: "features", label: "Features" },
  { id: "get-paid", label: "Getting paid" },
];

// Mounts the header above two sections, at `path`.
const mount = async (path = "/", props = {}) => {
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../app/store");
  const { default: PublicHeader } = await import("./PublicHeader");
  let location;
  const Address = () => {
    location = useLocation();
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <PublicHeader sections={SECTIONS} {...props} />
          <section id="features" />
          <section id="get-paid" />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  return { page: container, address: () => location };
};

const link = (page, name) =>
  [...page.querySelectorAll("a")].find((a) => a.textContent.trim() === name);

const click = (element) =>
  act(async () => {
    element.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true, button: 0 }),
    );
  });

it("links each section by its id, before Log in and the sign-up button", async () => {
  const { page } = await mount("/", { signUpLabel: "Start for free" });
  const names = [...page.querySelectorAll("nav a")].map((a) =>
    a.textContent.trim(),
  );
  expect(names).toEqual([
    "Features",
    "Getting paid",
    "Log in",
    "Start for free",
  ]);
  expect(link(page, "Features").getAttribute("href")).toBe("/#features");
  expect(link(page, "Getting paid").getAttribute("href")).toBe("/#get-paid");
  expect(link(page, "Log in").getAttribute("href")).toBe("/login");
  expect(link(page, "Start for free").getAttribute("href")).toBe("/register");
});

it("keeps Get started and no section links by default", async () => {
  const { page } = await mount("/", { sections: undefined });
  const names = [...page.querySelectorAll("nav a")].map((a) =>
    a.textContent.trim(),
  );
  expect(names).toEqual(["Log in", "Get started"]);
  expect(scrolled).not.toHaveBeenCalled();
});

it("makes Log in the button where the page offers sign-up itself", async () => {
  const { page } = await mount("/", { signUp: false });
  const names = [...page.querySelectorAll("nav a")].map((a) =>
    a.textContent.trim(),
  );
  expect(names).toEqual(["Features", "Getting paid", "Log in"]);
  expect(link(page, "Log in").className).toContain("bg-spot-night");
});

it("scrolls smoothly to a section's address, and again on the next visit", async () => {
  const { page, address } = await mount();
  await click(link(page, "Getting paid"));
  expect(address().hash).toBe("#get-paid");
  expect(scrolled).toHaveBeenCalledTimes(1);
  expect(scrolled.mock.contexts[0]).toBe(page.querySelector("#get-paid"));
  expect(scrolled).toHaveBeenLastCalledWith({ behavior: "smooth" });
  await click(link(page, "Getting paid"));
  expect(scrolled).toHaveBeenCalledTimes(2);
});

it("jumps instead when the visitor prefers less motion", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({ matches: query.includes("reduce") })),
  );
  const { page } = await mount();
  await click(link(page, "Features"));
  expect(scrolled.mock.contexts[0]).toBe(page.querySelector("#features"));
  expect(scrolled).toHaveBeenLastCalledWith({ behavior: "auto" });
});

it("scrolls to the section a visitor arrives at, once the fonts are in", async () => {
  const fontsLoaded = fontsLoading();
  await mount("/#features");
  expect(scrolled).not.toHaveBeenCalled();
  await fontsLoaded();
  expect(scrolled.mock.contexts[0]).toBe(container.querySelector("#features"));
  expect(scrolledTo).not.toHaveBeenCalled();
});

it("scrolls to the section a visitor arrives at without waiting on fonts it cannot see", async () => {
  await mount("/#features");
  await vi.waitFor(() =>
    expect(scrolled.mock.contexts[0]).toBe(
      container.querySelector("#features"),
    ),
  );
});

it("goes back to the top from a section when the page's own address is opened", async () => {
  const { page } = await mount("/#features");
  await vi.waitFor(() => expect(scrolled).toHaveBeenCalledTimes(1));
  await click(page.querySelector("a[aria-label='Invoice Spot home']"));
  expect(scrolledTo).toHaveBeenCalledWith({ top: 0, behavior: "smooth" });
  expect(scrolled).toHaveBeenCalledTimes(1);
});

it("leaves the window alone on arrival at the page's own address, and on a page without sections", async () => {
  await mount("/");
  unmount();
  await mount("/#features", { sections: undefined });
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(scrolled).not.toHaveBeenCalled();
  expect(scrolledTo).not.toHaveBeenCalled();
});
