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
  delete document.visibilityState;
  vi.useRealTimers();
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

// The clock, faked before the page mounts so the tour, the headline and
// the steps can be driven by it; `pass` moves it on.
const clock = () =>
  vi.useFakeTimers({
    toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval"],
  });
const pass = (ms) =>
  act(async () => {
    vi.advanceTimersByTime(ms);
  });

// Whether the visitor prefers less motion.
const stillVisitor = () =>
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({ matches: query.includes("reduce") })),
  );

// Whether the tab is being looked at.
const tab = (visibilityState) => {
  Object.defineProperty(document, "visibilityState", {
    value: visibilityState,
    configurable: true,
  });
  return act(async () => {
    document.dispatchEvent(new Event("visibilitychange"));
  });
};

// An observer the test drives: it records what it was asked to watch and
// reports it on screen, or not, on `report`.
let observers;
class Observer {
  constructor(callback, options) {
    this.callback = callback;
    this.options = options;
    this.watched = [];
    observers.push(this);
  }
  observe(element) {
    this.watched.push(element);
  }
  disconnect() {}
  report(isIntersecting) {
    return act(async () => this.callback([{ isIntersecting }]));
  }
}
const watch = () => {
  observers = [];
  vi.stubGlobal("IntersectionObserver", Observer);
};

// The hero, and the parts of its tour.
const hero = (page) => page.querySelector("[aria-labelledby=hero-title]");
const screens = (page) => [...hero(page).querySelectorAll("img")];
const shown = (page) =>
  screens(page).map((img) => img.classList.contains("opacity-100"));
const overlay = (page) =>
  screens(page)[0].parentElement.querySelector("[aria-hidden=true]");
const pointer = (page) => overlay(page).querySelector(":scope > svg");
const highlight = (page) => overlay(page).querySelector(":scope > span");
const note = (page) =>
  [...overlay(page).querySelectorAll("span")]
    .map((span) => span.textContent)
    .find((text) => text);
const dot = (page, name) => page.querySelector(`[aria-label="Show ${name}"]`);
const screenName = (page) => page.querySelector("[aria-live]");
const place = (element) => [element.style.left, element.style.top];
const area = (element) => [
  element.style.left,
  element.style.top,
  element.style.width,
  element.style.height,
];
const ticker = (page) => page.querySelector("h1 [aria-hidden=true] > span");
// The step the page is on, by its title.
const currentStep = (page) =>
  page.querySelector("#get-paid [aria-current=step]").lastElementChild
    .firstElementChild.textContent;

it("links sign-up and sign-in", async () => {
  const { page } = await open();
  expect(page.querySelectorAll("h1")).toHaveLength(1);
  expect(page.querySelector("h1").textContent).toMatch(
    /^Invoice in minutes\.Get paid /,
  );
  const register = links(page, "Start for free");
  expect(register.length).toBeGreaterThan(0);
  register.forEach((link) =>
    expect(link.getAttribute("href")).toBe("/register"),
  );
  expect(links(page, "Log in")[0].getAttribute("href")).toBe("/login");
});

it("ends the headline one way at a time, and reads it to assistive technology once", async () => {
  clock();
  const { page } = await open();
  const h1 = page.querySelector("h1");
  const endings = [...ticker(page).children].map((span) => span.textContent);
  expect(endings).toEqual([
    "without the chase.",
    "by UPI or bank.",
    "on time.",
  ]);
  expect(ticker(page).parentElement.getAttribute("aria-hidden")).toBe("true");
  expect(h1.querySelector(".sr-only").textContent).toBe("without the chase.");
  expect(ticker(page).style.transform).toBe("translateY(0em)");
  await pass(2800);
  expect(ticker(page).style.transform).toBe("translateY(-1.08em)");
  await pass(2800);
  expect(ticker(page).style.transform).toBe("translateY(-2.16em)");
  await pass(2800);
  expect(ticker(page).style.transform).toBe("translateY(0em)");
});

it("links each section of the page from the header", async () => {
  const { page } = await open();
  for (const [name, id, title] of [
    ["How it works", "get-paid", "paid-title"],
    ["Pricing", "pricing", "pricing-title"],
  ]) {
    const [link] = links(page, name);
    expect(link.getAttribute("href")).toBe(`/#${id}`);
    const section = page.querySelector(`section#${id}`);
    expect(section.getAttribute("aria-labelledby")).toBe(title);
    expect(section.querySelector(`#${title}`)).not.toBeNull();
    await click(link);
    expect(Element.prototype.scrollIntoView.mock.contexts.at(-1)).toBe(section);
  }
});

it("signs in as the guest and opens the dashboard", async () => {
  const { page, seen, store } = await open("/", {
    login: { username: "guest", accessToken: token },
  });
  await click(button(page, "See the live demo"));
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
  await click(button(page, "See the live demo"));
  await vi.waitFor(() =>
    expect(page.querySelector("[role=alert]")?.textContent).toBe(
      "The sample business is resting",
    ),
  );
  expect(seen.at(-1)).toBe("/");
});

it("opens the dashboard straight away when already signed in", async () => {
  const { page, seen } = await open("/", { signedIn: true });
  await click(button(page, "See the live demo"));
  expect(seen.at(-1)).toBe("/dashboard");
  expect(requests).toEqual([]);
});

it("shows the tour's four screens, the first one eagerly and sized", async () => {
  const { page } = await open();
  expect(screens(page).map((img) => img.alt)).toEqual([
    "The Overview: total receivables by age, billed with its tax, received and profit.",
    "Receivables: overdue invoices by age, with a reminder button on each.",
    "Sales: what was billed, by type and by tax rate.",
    "Payments: what came in by payment method, and who paid most.",
  ]);
  screens(page).forEach((img) => {
    expect(img.getAttribute("width")).toBe("1440");
    expect(img.getAttribute("height")).toBe("720");
  });
  expect(screens(page).map((img) => img.getAttribute("loading"))).toEqual([
    "eager",
    "lazy",
    "lazy",
    "lazy",
  ]);
  expect(shown(page)).toEqual([true, false, false, false]);
});

it("plays the tour: the pointer clicks one thing on each screen, then the next follows", async () => {
  clock();
  const { page } = await open();
  expect(overlay(page).getAttribute("aria-hidden")).toBe("true");
  expect(place(pointer(page))).toEqual(["84%", "90%"]);
  expect(highlight(page)).toBeNull();
  await pass(700);
  expect(place(pointer(page))).toEqual(["44%", "48%"]);
  expect(highlight(page)).toBeNull();
  await pass(1200);
  expect(area(highlight(page))).toEqual(["37.5%", "41%", "15%", "11%"]);
  expect(note(page)).toBe("3 invoices overdue · 6,326.03");
  expect(screenName(page).textContent).toBe("Overview");
  expect(screenName(page).getAttribute("aria-live")).toBe("off");
  await pass(2900);
  expect(shown(page)).toEqual([false, true, false, false]);
  expect(screenName(page).textContent).toBe("Receivables");
  expect(place(pointer(page))).toEqual(["84%", "90%"]);
  expect(highlight(page)).toBeNull();
  expect(dot(page, "Receivables").getAttribute("aria-pressed")).toBe("true");
  expect(dot(page, "Overview").getAttribute("aria-pressed")).toBe("false");
  await pass(1900);
  expect(place(pointer(page))).toEqual(["93%", "73%"]);
  expect(area(highlight(page))).toEqual(["21.5%", "69.5%", "76.5%", "6.5%"]);
  expect(note(page)).toBe("Reminder sent for INV-1026");
  await pass(2900);
  expect(shown(page)).toEqual([false, false, true, false]);
  await pass(4800);
  expect(shown(page)).toEqual([false, false, false, true]);
  await pass(4800);
  expect(shown(page)).toEqual([true, false, false, false]);
});

it("stops the tour on the screen a dot picks, and says which", async () => {
  clock();
  const { page } = await open();
  await pass(1000);
  await click(dot(page, "Sales"));
  expect(shown(page)).toEqual([false, false, true, false]);
  expect(dot(page, "Sales").getAttribute("aria-pressed")).toBe("true");
  expect(screenName(page).textContent).toBe("Sales");
  expect(screenName(page).getAttribute("aria-live")).toBe("polite");
  expect(place(pointer(page))).toEqual(["80%", "43%"]);
  expect(area(highlight(page))).toEqual(["60.5%", "31%", "37.5%", "19.5%"]);
  expect(note(page)).toBe("Includes 4,654.08 tax at 7.25%");
  await pass(20000);
  expect(shown(page)).toEqual([false, false, true, false]);
  expect(note(page)).toBe("Includes 4,654.08 tax at 7.25%");
  await click(dot(page, "Payments"));
  expect(shown(page)).toEqual([false, false, false, true]);
  expect(area(highlight(page))).toEqual(["60.5%", "55.5%", "37.5%", "6.5%"]);
  expect(note(page)).toBe("Top payer: Quill & Co. · 11,490.77");
});

it("holds the first screen still, with no pointer, for a visitor who prefers less motion", async () => {
  stillVisitor();
  clock();
  const { page } = await open();
  expect(page.querySelectorAll("[class*='animate-']")).toHaveLength(0);
  expect(pointer(page)).toBeNull();
  expect(area(highlight(page))).toEqual(["37.5%", "41%", "15%", "11%"]);
  expect(note(page)).toBe("3 invoices overdue · 6,326.03");
  await pass(20000);
  expect(shown(page)).toEqual([true, false, false, false]);
  expect(note(page)).toBe("3 invoices overdue · 6,326.03");
  expect(ticker(page).style.transform).toBe("translateY(0em)");
  expect(currentStep(page)).toBe("Send");
  // The step's bar is there, full and still.
  const stepBar = page.querySelector(
    "#get-paid [aria-current=step] [aria-hidden=true]",
  );
  expect(stepBar).not.toBeNull();
  expect(stepBar.firstElementChild.className).not.toContain("animate-");
  await click(dot(page, "Payments"));
  expect(shown(page)).toEqual([false, false, false, true]);
  expect(note(page)).toBe("Top payer: Quill & Co. · 11,490.77");
  expect(pointer(page)).toBeNull();
});

it("pauses the tour and the headline while the hero is off screen or the tab is hidden", async () => {
  watch();
  clock();
  const { page } = await open();
  const watcher = observers.find((observer) =>
    observer.watched.includes(hero(page)),
  );
  // Nothing moves until the hero is seen on screen.
  await pass(5000);
  expect(place(pointer(page))).toEqual(["84%", "90%"]);
  expect(ticker(page).style.transform).toBe("translateY(0em)");
  await watcher.report(true);
  await pass(700);
  expect(place(pointer(page))).toEqual(["44%", "48%"]);
  // Scrolled away, the step stops where it is, and starts over on return.
  await watcher.report(false);
  await pass(5000);
  expect(place(pointer(page))).toEqual(["44%", "48%"]);
  expect(highlight(page)).toBeNull();
  expect(ticker(page).style.transform).toBe("translateY(0em)");
  await watcher.report(true);
  expect(place(pointer(page))).toEqual(["84%", "90%"]);
  await pass(1900);
  expect(note(page)).toBe("3 invoices overdue · 6,326.03");
  await pass(900);
  expect(ticker(page).style.transform).toBe("translateY(-1.08em)");
  // In a tab that is not being looked at, the same.
  await tab("hidden");
  await pass(10000);
  expect(note(page)).toBe("3 invoices overdue · 6,326.03");
  expect(ticker(page).style.transform).toBe("translateY(-1.08em)");
  await tab("visible");
  await pass(4800);
  expect(shown(page)).toEqual([false, true, false, false]);
  expect(ticker(page).style.transform).toBe("translateY(-2.16em)");
});

it("shows the client's phone as one picture, and what is on it", async () => {
  const { page } = await open();
  const phone = page.querySelector("[role=img]");
  expect(phone.getAttribute("aria-label")).toBe(
    "What your client sees on their phone: the invoice, a UPI QR, Open in UPI app and Report a payment.",
  );
  for (const line of [
    "Tester Studio",
    "INV-0042",
    "Due Oct 18, 2026",
    "Amount due",
    "₹ 59,000.00",
    "Open in UPI app",
    "Report a payment",
  ]) {
    expect(phone.textContent).toContain(line);
  }
  expect(phone.querySelector("svg")).not.toBeNull();
  expect(phone.querySelector("a, button, input, [tabindex]")).toBeNull();
});

it("plays the steps from invoice to paid, and lets a click pick one", async () => {
  clock();
  const { page } = await open();
  const section = page.querySelector("section#get-paid");
  const stepButtons = () => [...section.querySelectorAll("ol button")];
  const stepButton = (name) =>
    stepButtons().find((element) => element.textContent.includes(name));
  const overlayText = () =>
    [...section.querySelectorAll("[data-template] > [aria-hidden=true]")]
      .map((element) => element.textContent)
      .join(" | ");
  expect(
    stepButtons().map(
      (element) => element.lastElementChild.firstElementChild.textContent,
    ),
  ).toEqual(["Send", "They pay", "You confirm"]);
  // Every step keeps its full colour; only the one playing has the bar
  // under its line, running out.
  const items = () => [...section.querySelectorAll("ol > li")];
  const bar = (item) => item.querySelector("button [aria-hidden=true]");
  expect(items().some((item) => /opacity/.test(item.className))).toBe(false);
  expect(items().map((item) => Boolean(bar(item)))).toEqual([
    true,
    false,
    false,
  ]);
  expect(bar(items()[0]).firstElementChild.className).toContain(
    "animate-home-progress",
  );
  expect(currentStep(page)).toBe("Send");
  expect(overlayText()).toBe("Sent by email and WhatsApp");
  expect(section.textContent).toContain("Rupees Fifty-Nine Thousand Only");
  await pass(2800);
  expect(currentStep(page)).toBe("They pay");
  expect(overlayText()).toBe(
    "Payment reported · just nowHarbor & Pine paid ₹ 59,000.00 by UPIConfirm",
  );
  await pass(2800);
  expect(currentStep(page)).toBe("You confirm");
  expect(overlayText()).toBe("PAID IN FULL");
  expect(section.textContent).toContain("Balance due 0.00");
  expect(section.textContent).not.toContain("Rupees Fifty-Nine Thousand Only");
  await pass(2800);
  expect(currentStep(page)).toBe("Send");
  await pass(1500);
  await click(stepButton("They pay"));
  expect(currentStep(page)).toBe("They pay");
  expect(overlayText()).toContain("Confirm");
  expect(stepButton("They pay").getAttribute("aria-current")).toBe("step");
  expect(stepButton("Send").getAttribute("aria-current")).toBeNull();
  expect(items().map((item) => Boolean(bar(item)))).toEqual([
    false,
    true,
    false,
  ]);
  // The picked step has its full turn from the click, even when it was
  // the step shown already, and its bar starts over.
  await pass(2000);
  expect(currentStep(page)).toBe("They pay");
  await pass(800);
  expect(currentStep(page)).toBe("You confirm");
  await pass(2000);
  const fill = bar(items()[2]).firstElementChild;
  await click(stepButton("You confirm"));
  expect(bar(items()[2]).firstElementChild).not.toBe(fill);
  await pass(2000);
  expect(currentStep(page)).toBe("You confirm");
  await pass(800);
  expect(currentStep(page)).toBe("Send");
});

it("turns the steps only while they are on screen", async () => {
  watch();
  clock();
  const { page } = await open();
  const section = page.querySelector("section#get-paid");
  const watcher = observers.find(
    (observer) => observer.watched.includes(section) && !observer.options,
  );
  await pass(5000);
  expect(currentStep(page)).toBe("Send");
  await watcher.report(true);
  await pass(2800);
  expect(currentStep(page)).toBe("They pay");
  await watcher.report(false);
  await pass(5000);
  expect(currentStep(page)).toBe("They pay");
});

it("dresses the sample invoice in the chosen PDF template", async () => {
  const { page } = await open();
  const group = page.querySelector("[role=group][aria-label='PDF template']");
  const choices = [...group.querySelectorAll("button")];
  expect(choices.map((element) => element.textContent)).toEqual([
    "Classic",
    "Modern",
    "Compact",
  ]);
  const pressed = () =>
    choices.map((element) => element.getAttribute("aria-pressed"));
  const mock = () => page.querySelector("[data-template]");
  expect(pressed()).toEqual(["true", "false", "false"]);
  expect(mock().dataset.template).toBe("classic");
  for (const line of [
    "TAX INVOICE",
    "INV-0042",
    "Tester Studio",
    "GSTIN 27ABCDE1234F1Z5",
    "Bill to Harbor & Pine Architects · Maharashtra (27)",
    "Brand identity designHSN 99839140,000.00",
    "Website design, 5 pagesHSN 99831410,000.00",
    "Pay by UPIyourstudio@upi",
    "CGST 9% + SGST 9% · 9,000.00",
    "₹ 59,000.00",
  ]) {
    expect(mock().textContent).toContain(line);
  }
  expect(mock().querySelector("[role=img]").getAttribute("aria-label")).toBe(
    "A sample UPI QR pattern",
  );
  expect(page.textContent).toContain("Sample invoice");
  await click(choices[1]);
  expect(pressed()).toEqual(["false", "true", "false"]);
  expect(mock().dataset.template).toBe("modern");
  expect(mock().firstElementChild.classList.contains("bg-spot-violet")).toBe(
    true,
  );
  await click(choices[2]);
  expect(pressed()).toEqual(["false", "false", "true"]);
  expect(mock().dataset.template).toBe("compact");
  expect(mock().classList.contains("text-[12.8px]")).toBe(true);
  expect(mock().firstElementChild.classList.contains("bg-spot-violet")).toBe(
    false,
  );
  // The template changes the dress, not the step.
  expect(mock().textContent).toContain("Rupees Fifty-Nine Thousand Only");
});

it("counts what the app covers, under the hero", async () => {
  const { page } = await open();
  const facts = page.querySelector("section[aria-label='In numbers']");
  expect([...facts.querySelectorAll("dt")].map((dt) => dt.textContent)).toEqual(
    ["179", "GST-ready", "4 roles", "1 step"],
  );
  expect([...facts.querySelectorAll("dd")].map((dd) => dd.textContent)).toEqual(
    [
      "currencies to invoice in",
      "CGST, SGST or IGST by place of supply",
      "for your team, owner to viewer",
      "to confirm a payment",
    ],
  );
});

it("ends on free pricing and three questions, with sign-up", async () => {
  const { page } = await open();
  const pricing = page.querySelector("section#pricing");
  expect(pricing.querySelector("h2").textContent).toBe("Free.");
  expect(pricing.textContent).toContain(
    "Everything included, for you and your team. No card needed.",
  );
  expect(
    [...pricing.querySelectorAll("dt")].map((dt) => dt.textContent),
  ).toEqual(["What's included?", "Do I need a GSTIN?", "Is my data safe?"]);
  expect(
    [...pricing.querySelectorAll("dd")].map((dd) => dd.textContent),
  ).toEqual([
    "Invoices, quotations and recurring invoices, UPI and bank payments, reminders, the client portal, reports and team roles, in any currency.",
    "No. Without one, invoices use simple tax rates and no GST terms.",
    "Two-factor sign-in, links you can revoke, and a full export whenever you want it.",
  ]);
  expect(links(pricing, "Start for free")[0].getAttribute("href")).toBe(
    "/register",
  );
});

it("signs off with the year", async () => {
  const { page } = await open();
  expect(page.querySelector("footer").textContent).toBe(
    `Invoice Spot.© ${new Date().getFullYear()} Invoice Spot · Made for independent business`,
  );
});
