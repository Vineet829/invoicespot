import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PortalDialog from "./PortalDialog";

// The dialog reads the link's status and acts on it through these hooks, so
// it needs no store or server; the browser's modal is left out.
const status = vi.hoisted(() => ({ current: null }));
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../customersApiSlice", () => ({
  useGetPortalStatusQuery: () => ({
    data: status.current,
    isFetching: false,
    isError: false,
  }),
  useCreatePortalLinkMutation: () => idle(),
  useRevokePortalLinkMutation: () => idle(),
  useEmailPortalMutation: () => idle(),
  useSharePortalOnWhatsAppMutation: () => idle(),
  useGetPortalEmailDraftQuery: () => ({}),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));
vi.mock("../../documents/components/EmailDocumentDialog", () => ({
  EmailDialog: () => null,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

const VIEWER = "Viewers can't change anything in this business";
const ON = {
  active: true,
  createdAt: "2026-01-15T10:00:00Z",
  viewCount: 2,
  lastViewedAt: "2026-01-16T10:00:00Z",
  url: null,
};

const render = (props) => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <PortalDialog
        customer={{ _id: "c1", name: "Acme" }}
        open
        onClose={() => {}}
        {...props}
      />,
    );
  });
  return { container, root };
};

const buttons = (container) =>
  [...container.querySelectorAll("button")].map((button) =>
    button.textContent.trim(),
  );

it("shows a viewer that a link is on, but not the link or the ways to change or send it", () => {
  status.current = ON;
  const { container, root } = render({ refusal: VIEWER });
  expect(container.textContent).toContain("Opened 2 times");
  expect(container.textContent).toContain(
    "Only members who can send the link see it.",
  );
  expect(container.textContent).not.toContain("can’t be shown again");
  expect(buttons(container)).toEqual([]);
  act(() => root.unmount());
});

it("tells a viewer when no link is on, without a way to make one", () => {
  status.current = { active: false };
  const { container, root } = render({ refusal: VIEWER });
  expect(container.textContent).toContain("No link is on.");
  expect(buttons(container)).toEqual([]);
  act(() => root.unmount());
});

it("gives a member who can send the link the ways to change and send it", () => {
  status.current = ON;
  const { container, root } = render({});
  expect(buttons(container)).toEqual(
    expect.arrayContaining([
      "Turn off link",
      "Make a new link",
      "Email the link",
      "Share on WhatsApp",
    ]),
  );
  act(() => root.unmount());
});
