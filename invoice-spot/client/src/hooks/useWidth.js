import { useLayoutEffect, useRef, useState } from "react";

// An element's width, kept current as it resizes, so a chart can be drawn at
// its real size. Until the element is measured, `fallback` stands in.
const useWidth = (fallback) => {
  const ref = useRef(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    setWidth(Math.round(element.getBoundingClientRect().width));
    if (typeof ResizeObserver === "undefined") return undefined;
    const observer = new ResizeObserver(([entry]) =>
      setWidth(Math.round(entry.contentRect.width)),
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width > 0 ? width : fallback];
};

export default useWidth;
