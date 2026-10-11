import { afterEach, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { store } from "../../../app/store";
import VerifiedPage from "./VerifiedPage";

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const show = (address) => {
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  act(() =>
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[address]}>
          <VerifiedPage />
        </MemoryRouter>
      </Provider>,
    ),
  );
  return container;
};

it.each([
  ["/auth/verify", "Account verified.", "/login"],
  ["/auth/verify?already=1", "Account already verified.", "/login"],
  ["/auth/verify?expired=1", "This link has expired.", "/resend"],
])("%s says how the link went", (address, title, next) => {
  const page = show(address);
  expect(page.querySelector("h1, h2").textContent).toBe(title);
  expect(page.querySelector(`a[href="${next}"]`)).not.toBeNull();
});
