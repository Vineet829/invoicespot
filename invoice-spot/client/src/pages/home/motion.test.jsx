import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { useReveal } from "./motion";

window.IS_REACT_ACT_ENVIRONMENT = true;

// An observer the test drives: it records what it was asked to watch and
// reports it in view on `enter`.
let observers;
class Observer {
  constructor(callback, options) {
    this.callback = callback;
    this.options = options;
    this.watched = [];
    this.disconnect = vi.fn();
    observers.push(this);
  }
  observe(element) {
    this.watched.push(element);
  }
  enter() {
    this.callback([{ isIntersecting: true }]);
  }
}

const Panel = ({ motion }) => {
  const [ref, classes] = useReveal(motion);
  return <section ref={ref} className={classes} />;
};

let root;
let container;
let mounted;
beforeEach(() => {
  observers = [];
  vi.stubGlobal("IntersectionObserver", Observer);
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  mounted = false;
});
afterEach(() => {
  if (mounted) unmount();
  container.remove();
  vi.unstubAllGlobals();
});

const mount = async (motion) => {
  await act(async () => root.render(<Panel motion={motion} />));
  mounted = true;
  return container.querySelector("section");
};
const unmount = () => {
  act(() => root.unmount());
  mounted = false;
};

it("keeps a section out of sight until it comes into view, then rises it in once", async () => {
  const section = await mount(true);
  expect(section.className).toBe("opacity-0 print:opacity-100");
  expect(observers).toHaveLength(1);
  expect(observers[0].watched).toEqual([section]);
  expect(observers[0].options).toEqual({ rootMargin: "0px 0px -10% 0px" });
  await act(async () => observers[0].callback([{ isIntersecting: false }]));
  expect(section.className).toBe("opacity-0 print:opacity-100");
  await act(async () => observers[0].enter());
  expect(section.className).toBe("animate-ov-rise print:animate-none");
  expect(observers[0].disconnect).toHaveBeenCalled();
});

it("stops watching a section that goes away", async () => {
  await mount(true);
  unmount();
  expect(observers[0].disconnect).toHaveBeenCalledTimes(1);
});

it("shows a section straight away for a visitor who prefers less motion", async () => {
  const section = await mount(false);
  expect(section.className).toBe("");
  expect(observers).toHaveLength(0);
});

it("shows a section straight away where nothing can watch it", async () => {
  vi.stubGlobal("IntersectionObserver", undefined);
  const section = await mount(true);
  expect(section.className).toBe("animate-ov-rise print:animate-none");
});
