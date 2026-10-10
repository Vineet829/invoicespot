import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import {
  Link,
  MemoryRouter,
  Route,
  Routes,
  useNavigate,
} from "react-router-dom";
import ScrollToTop from "./ScrollToTop";

window.IS_REACT_ACT_ENVIRONMENT = true;

let scrollTo;
let navigate;
let root;
let container;
beforeEach(() => {
  scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

const Page = () => {
  navigate = useNavigate();
  return (
    <>
      <Link to="/documents">Documents</Link>
      <Link to="/reports#ageing">Unpaid invoices by age</Link>
      <Link to="/dashboard?period=30d">30 days</Link>
    </>
  );
};

const render = () => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <ScrollToTop />
        <Routes>
          <Route path="*" element={<Page />} />
        </Routes>
      </MemoryRouter>,
    );
  });
};
const follow = (text) =>
  act(() => {
    [...container.querySelectorAll("a")]
      .find((link) => link.textContent === text)
      .dispatchEvent(
        new MouseEvent("click", { bubbles: true, cancelable: true }),
      );
  });

it("opens the page a link leads to at its top", () => {
  render();
  expect(scrollTo).not.toHaveBeenCalled();
  follow("Documents");
  expect(scrollTo).toHaveBeenCalledWith(0, 0);
});

it("keeps the place for the same page, a section, and going back", () => {
  render();
  follow("30 days");
  follow("Unpaid invoices by age");
  act(() => navigate(-1));
  expect(scrollTo).not.toHaveBeenCalled();
});
