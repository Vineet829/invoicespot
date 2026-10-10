import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { useInterval, useReveal, useRunning } from "./motion";

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

// Something that plays while it is on screen in a tab being looked at.
const Player = ({ motion }) => {
  const ref = useRef(null);
  const running = useRunning(ref, motion);
  return <section ref={ref} data-running={running} />;
};
const show = async (element) => {
  await act(async () => root.render(element));
  mounted = true;
  return container.firstElementChild;
};

it("plays while the element is on screen and the tab is being looked at", async () => {
  const section = await show(<Player motion />);
  expect(section.dataset.running).toBe("false");
  expect(observers[0].watched).toEqual([section]);
  await act(async () => observers[0].enter());
  expect(section.dataset.running).toBe("true");
  await act(async () => observers[0].callback([{ isIntersecting: false }]));
  expect(section.dataset.running).toBe("false");
  await act(async () => observers[0].enter());
  Object.defineProperty(document, "visibilityState", {
    value: "hidden",
    configurable: true,
  });
  await act(async () => document.dispatchEvent(new Event("visibilitychange")));
  expect(section.dataset.running).toBe("false");
  delete document.visibilityState;
  await act(async () => document.dispatchEvent(new Event("visibilitychange")));
  expect(section.dataset.running).toBe("true");
});

it("never plays for a visitor who prefers less motion, and plays where nothing can watch it", async () => {
  const still = await show(<Player motion={false} />);
  expect(still.dataset.running).toBe("false");
  await act(async () => observers[0].enter());
  expect(still.dataset.running).toBe("false");
  unmount();
  root = createRoot(container);
  vi.stubGlobal("IntersectionObserver", undefined);
  const section = await show(<Player motion />);
  expect(section.dataset.running).toBe("true");
});

// A count that goes up by `step` every second while `running`.
const Counter = ({ running, step }) => {
  const [count, setCount] = useState(0);
  useInterval(() => setCount((current) => current + step), 1000, running);
  return <output>{count}</output>;
};

it("ticks while running, with the latest tick", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  try {
    const pass = (ms) =>
      act(async () => {
        vi.advanceTimersByTime(ms);
      });
    const output = await show(<Counter running step={1} />);
    await pass(1000);
    expect(output.textContent).toBe("1");
    await show(<Counter running step={10} />);
    await pass(1000);
    expect(output.textContent).toBe("11");
    await show(<Counter running={false} step={10} />);
    await pass(5000);
    expect(output.textContent).toBe("11");
    await show(<Counter running step={100} />);
    await pass(1000);
    expect(output.textContent).toBe("111");
  } finally {
    vi.useRealTimers();
  }
});
