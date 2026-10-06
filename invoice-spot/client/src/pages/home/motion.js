import { useEffect, useRef, useState } from "react";
import usePageVisible from "../../hooks/usePageVisible";

// Whether the visitor asked for less motion, read as the page mounts. The
// stylesheet stills every animation for them; the page also leaves its
// entrances out, so nothing waits on an animation to be seen.
export const usePrefersReducedMotion = () => {
  const [reduced] = useState(
    () =>
      window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false,
  );
  return reduced;
};

// A section rises in the first time it comes into view, and is kept out of
// sight until then: [ref for the section, its classes]. Without an
// observer, or with motion off, it is simply there, and a printed page
// shows every section.
export const useReveal = (motion) => {
  const ref = useRef(null);
  const [shown, setShown] = useState(
    () => !motion || typeof IntersectionObserver === "undefined",
  );
  useEffect(() => {
    if (shown) return undefined;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        setShown(true);
        observer.disconnect();
      },
      { rootMargin: "0px 0px -10% 0px" },
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [shown]);
  return [
    ref,
    !motion
      ? ""
      : shown
        ? "animate-ov-rise print:animate-none"
        : "opacity-0 print:opacity-100",
  ];
};

// Whether the element at `ref` is on screen, kept current as the visitor
// scrolls. Without an observer it counts as on screen.
export const useOnScreen = (ref) => {
  const [onScreen, setOnScreen] = useState(
    () => typeof IntersectionObserver === "undefined",
  );
  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") return undefined;
    const observer = new IntersectionObserver(([entry]) =>
      setOnScreen(entry.isIntersecting),
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [ref]);
  return onScreen;
};

// Whether something that plays on its own should be playing: the visitor
// is fine with motion, the element at `ref` is on screen and its tab is
// the one being looked at.
export const useRunning = (ref, motion) => {
  const onScreen = useOnScreen(ref);
  const visible = usePageVisible();
  return motion && onScreen && visible;
};

// Calls the latest `tick` every `ms` milliseconds while `running`.
export const useInterval = (tick, ms, running) => {
  const latest = useRef(tick);
  useEffect(() => {
    latest.current = tick;
  });
  useEffect(() => {
    if (!running) return undefined;
    const id = setInterval(() => latest.current(), ms);
    return () => clearInterval(id);
  }, [ms, running]);
};
