import { afterAll, afterEach, beforeAll, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { moment } from "./ActivityTimeline";

window.IS_REACT_ACT_ENVIRONMENT = true;

// The viewer is in India, hours ahead of a business in Los Angeles.
const zone = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "Asia/Kolkata";
});
afterAll(() => {
  if (zone === undefined) delete process.env.TZ;
  else process.env.TZ = zone;
});

// Spaces as plain spaces, whatever kind the browser puts before "PM".
const plain = (text) => text.replace(/\s/g, " ");

// 23:30 UTC on Oct 7 is 4:30 PM that day in Los Angeles, and 5:00 AM on
// Oct 8 in Kolkata.
const AT = "2026-10-07T23:30:00Z";

it("gives an event's time in the business's zone, naming the zone when it isn't the viewer's", () => {
  expect(plain(moment(AT, "America/Los_Angeles"))).toBe(
    "Oct 7, 2026, 4:30 PM PDT",
  );
  expect(plain(moment(AT, "Asia/Kolkata"))).toBe("Oct 8, 2026, 5:00 AM");
});

it("gives it in the viewer's zone while the business names none the browser knows", () => {
  expect(plain(moment(AT))).toBe("Oct 8, 2026, 5:00 AM");
  expect(plain(moment(AT, "Nowhere/Town"))).toBe("Oct 8, 2026, 5:00 AM");
});

let root;
let container;
afterEach(() => {
  if (root) act(() => root.unmount());
  container?.remove();
  root = undefined;
  container = undefined;
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json" },
  });

// Drawn through the app's own store and RTK Query, with only fetch faked;
// the modules are read afresh so the store takes the API's address.
it("shows the document's activity in the active business's zone", async () => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const path = new URL(request.url).pathname.replace("/api/v1", "");
      return json(
        {
          "/businesses": {
            businesses: [
              { _id: "b-1", active: true, timeZone: "America/Los_Angeles" },
            ],
          },
          "/document/d-1/activity": {
            events: [{ type: "CREATED", at: AT, byYou: true }],
          },
        }[path],
      );
    }),
  );
  const { Provider } = await import("react-redux");
  const { store } = await import("../../../app/store");
  const { default: ActivityTimeline } = await import("./ActivityTimeline");
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <ActivityTimeline documentId="d-1" />
      </Provider>,
    );
  });
  await act(() =>
    vi.waitFor(() =>
      expect(plain(container.querySelector("time")?.textContent || "")).toBe(
        "Oct 7, 2026, 4:30 PM PDT",
      ),
    ),
  );
  expect(plain(container.querySelector("li").textContent)).toContain("Created");
  expect(container.querySelector("time").getAttribute("dateTime")).toBe(AT);
});
