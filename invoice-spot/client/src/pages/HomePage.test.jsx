import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { GUEST_CREDENTIALS } from "../features/auth/components/authCommon";

// The homepage as the app draws it, through its own store and RTK Query,
// with only fetch faked.
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

let requests;
let root;
let container;
beforeEach(() => {
  localStorage.clear();
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  requests = [];
  Element.prototype.scrollIntoView = vi.fn();
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  delete Element.prototype.scrollIntoView;
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

// Answers the sign-in request with `login`, a body or a Response, and
// records every request's method, path and body.
const serve = (login) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const path = new URL(request.url).pathname.replace("/api/v1", "");
      requests.push({
        method: request.method,
        path,
        body: request.method === "POST" ? await request.json() : null,
      });
      if (path !== "/auth/login") return json({ message: "Not found" }, 404);
      return login instanceof Response ? login : json(login);
    }),
  );

// The page at `path`, with the paths it moves to and the store.
const open = async (path = "/", { login, signedIn = false } = {}) => {
  serve(login);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, Routes, Route, useLocation } =
    await import("react-router-dom");
  const { store } = await import("../app/store");
  const { logIn } = await import("../features/auth/authSlice");
  const { default: HomePage } = await import("./HomePage");
  if (signedIn) store.dispatch(logIn({ username: "asha", accessToken: token }));
  const seen = [];
  const Probe = () => {
    seen.push(useLocation().pathname);
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <Probe />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="*" element={null} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
  });
  return { page: container, seen, store };
};

const links = (page, name) =>
  [...page.querySelectorAll("a")].filter(
    (link) => link.textContent.trim() === name,
  );
const button = (page, name) =>
  [...page.querySelectorAll("button")].find(
    (element) => element.textContent.trim() === name,
  );
const click = (element) =>
  act(async () => {
    element.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true, button: 0 }),
    );
  });

it("links sign-up and sign-in", async () => {
  const { page } = await open();
  expect(page.querySelectorAll("h1")).toHaveLength(1);
  expect(page.querySelector("h1").textContent).toBe(
    "Good work. Great invoices.",
  );
  const register = links(page, "Start for free");
  expect(register.length).toBeGreaterThan(0);
  register.forEach((link) =>
    expect(link.getAttribute("href")).toBe("/register"),
  );
  expect(links(page, "Log in")[0].getAttribute("href")).toBe("/login");
});

it("links each section of the page from the header", async () => {
  const { page } = await open();
  for (const [name, id] of [
    ["Features", "features"],
    ["Getting paid", "get-paid"],
    ["Templates", "templates"],
  ]) {
    const [link] = links(page, name);
    expect(link.getAttribute("href")).toBe(`/#${id}`);
    const section = page.querySelector(`section#${id}`);
    expect(section.getAttribute("aria-labelledby")).toBe(`${id}-title`);
    await click(link);
    expect(Element.prototype.scrollIntoView.mock.contexts.at(-1)).toBe(section);
  }
});

it("signs in as the guest and opens the dashboard", async () => {
  const { page, seen, store } = await open("/", {
    login: { username: "guest", accessToken: token },
  });
  await click(button(page, "Try as guest"));
  await vi.waitFor(() => expect(seen.at(-1)).toBe("/dashboard"));
  expect(requests).toEqual([
    { method: "POST", path: "/auth/login", body: GUEST_CREDENTIALS },
  ]);
  expect(store.getState().auth.user.username).toBe("guest");
});

it("says when the guest sign-in fails", async () => {
  const { page, seen } = await open("/", {
    login: json({ message: "The sample business is resting" }, 503),
  });
  await click(button(page, "Try as guest"));
  await vi.waitFor(() =>
    expect(page.querySelector("[role=alert]")?.textContent).toBe(
      "The sample business is resting",
    ),
  );
  expect(seen.at(-1)).toBe("/");
});

it("opens the dashboard straight away when already signed in", async () => {
  const { page, seen } = await open("/", { signedIn: true });
  expect(button(page, "Try as guest")).toBeUndefined();
  await click(button(page, "Open dashboard"));
  expect(seen.at(-1)).toBe("/dashboard");
  expect(requests).toEqual([]);
});

// The tile a label names, in the picture of the overview.
const tile = (page, title) =>
  [...page.querySelectorAll("span")]
    .find((span) => span.textContent === title)
    .closest("div");

it("shows the sample business's figures, each in its own tile", async () => {
  const { page } = await open();
  expect(tile(page, "Billed").textContent).toContain("68,848.08");
  expect(tile(page, "Billed").textContent).not.toContain("44,562.29");
  expect(tile(page, "Received").textContent).toContain("44,562.29");
  expect(tile(page, "Received").textContent).not.toContain("68,848.08");
  expect(tile(page, "Profit").textContent).toContain("58,279.33");
  expect(tile(page, "Outstanding now").textContent).toContain("24,285.79");
  expect(tile(page, "Outstanding now").textContent).toContain(
    "10 invoices · 3 overdue",
  );
  expect(tile(page, "Outstanding now").textContent).toContain(
    "Not due 17,959.76",
  );
  const text = page.textContent;
  for (const line of [
    "Pay INV-0042 by UPI",
    "₹ 59,000.00",
    "Scan with any UPI app",
    "Sample",
    "Payment reported",
    "Harbor & Pine · UPI · just now",
    "262.76",
    "Tax invoice INV-0042 issued",
    "CGST 9% + SGST 9% · sent by email",
  ]) {
    expect(text).toContain(line);
  }
});

it("keeps the picture from assistive technology, and says what it shows", async () => {
  const { page } = await open();
  const picture = tile(page, "Billed").closest("[aria-hidden=true]");
  expect(picture).not.toBeNull();
  expect(picture.textContent).toContain("Payment reported");
  expect(picture.querySelector("a, button, input, [tabindex]")).toBeNull();
  const words = [...page.querySelectorAll(".sr-only")].map(
    (element) => element.textContent,
  );
  expect(words.some((text) => text.startsWith("A sample of the product"))).toBe(
    true,
  );
});

it("keeps the payment placeholders as the app shows them", async () => {
  const { page } = await open();
  expect(page.textContent).toContain("UPI · yourstudio@upi");
  expect(page.textContent).toContain("Bank · IFSC ABCD0123456");
});

it("draws the sample invoice in each template, as choices", async () => {
  const { page } = await open();
  const radios = [...page.querySelectorAll("input[type=radio]")];
  expect(radios.map((radio) => radio.value)).toEqual([
    "classic",
    "modern",
    "compact",
  ]);
  expect(new Set(radios.map((radio) => radio.name)).size).toBe(1);
  // Each choice is its own drawing of the invoice, named by its caption.
  radios.forEach((radio) => {
    const [label] = radio.labels;
    expect(label.querySelector("[data-template]").dataset.template).toBe(
      radio.value,
    );
    expect(label.textContent).toContain("Harbor & Pine Architects");
  });
  expect(
    radios.map((radio) => radio.labels[0].lastElementChild.textContent),
  ).toEqual([
    "ClassicCalm and familiar",
    "ModernA bold band of colour",
    "CompactMore on one page",
  ]);
  expect(radios.map((radio) => radio.checked)).toEqual([true, false, false]);
  // A click, or Space and the arrow keys, check a radio.
  await act(async () => radios[1].click());
  expect(radios.map((radio) => radio.checked)).toEqual([false, true, false]);
  // Each can take keyboard focus.
  for (const radio of radios) {
    radio.focus();
    expect(document.activeElement).toBe(radio);
  }
});

it("rises in, except for a visitor who prefers less motion", async () => {
  const { page } = await open();
  expect(page.querySelectorAll("[class*='animate-']").length).toBeGreaterThan(
    0,
  );
  act(() => root.unmount());
  container.remove();
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({ matches: query.includes("reduce") })),
  );
  const still = await open();
  expect(still.page.querySelectorAll("[class*='animate-']")).toHaveLength(0);
  expect(still.page.querySelectorAll(".opacity-0")).toHaveLength(0);
});
