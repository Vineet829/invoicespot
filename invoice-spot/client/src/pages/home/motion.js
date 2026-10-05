import { useEffect, useRef, useState } from "react";

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

// Entrances by their delay. Tailwind needs each class written out.
const RISE = {
  0: "animate-ov-rise",
  120: "animate-ov-rise [--rise-delay:120ms]",
  240: "animate-ov-rise [--rise-delay:240ms]",
  320: "animate-ov-rise [--rise-delay:320ms]",
  400: "animate-ov-rise [--rise-delay:400ms]",
};

// The classes that make an element rise in after `delay` milliseconds, or
// none when motion is off. A delay not listed rises straight away.
export const rise = (motion, delay = 0) =>
  motion ? (RISE[delay] ?? RISE[0]) : "";

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
