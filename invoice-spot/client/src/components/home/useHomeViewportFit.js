import { useLayoutEffect, useRef } from "react";

const useHomeViewportFit = (contentKey) => {
  const viewportRef = useRef(null);
  const pageRef = useRef(null);

  useLayoutEffect(() => {
    const viewport = viewportRef.current;
    const page = pageRef.current;
    if (!viewport || !page) return undefined;

    let frame;
    let disposed = false;
    const fit = () => {
      if (disposed) return;
      page.style.setProperty("--landing-fit", "1");
      if (!window.matchMedia("(min-width: 851px)").matches) return;

      const fits = () => page.getBoundingClientRect().height <= viewport.clientHeight + 0.5;
      if (fits()) return;

      // Find the largest readable scale that keeps the footer inside the viewport.
      // Measuring after each change also accounts for text and chart-card reflow.
      let lower = 0;
      let upper = 1;
      for (let step = 0; step < 12; step += 1) {
        const candidate = (lower + upper) / 2;
        page.style.setProperty("--landing-fit", String(candidate));
        if (fits()) lower = candidate;
        else upper = candidate;
      }
      page.style.setProperty("--landing-fit", String(Math.floor(lower * 1000) / 1000));
    };
    const scheduleFit = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(fit);
    };

    fit();
    const observer = new ResizeObserver(scheduleFit);
    observer.observe(viewport);
    window.addEventListener("resize", scheduleFit);
    document.fonts.ready.then(() => !disposed && scheduleFit());

    return () => {
      disposed = true;
      cancelAnimationFrame(frame);
      observer.disconnect();
      window.removeEventListener("resize", scheduleFit);
    };
  }, [contentKey]);

  return { viewportRef, pageRef };
};

export default useHomeViewportFit;
