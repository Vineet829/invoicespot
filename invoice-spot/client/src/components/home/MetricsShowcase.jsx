import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { FiArrowUpRight, FiFileText, FiPieChart } from "react-icons/fi";
import usePrefersReducedMotion from "../../hooks/usePrefersReducedMotion";

// Illustrative rolling-period snapshots, independent of account data.
const snapshots = [
  { received: 24580, outstanding: 5400, growth: "12.8", points: [5500, 10200, 9700, 14600, 18700, 24580], invoices: [2, 5, 4, 7, 6, 3, 5] },
  { received: 26740, outstanding: 3950, growth: "17.2", points: [7000, 9700, 13100, 16100, 23600, 26740], invoices: [4, 5, 7, 4, 6, 3, 6] },
  { received: 25460, outstanding: 4480, growth: "15.1", points: [6100, 12600, 12100, 16800, 21900, 25460], invoices: [3, 6, 4, 6, 5, 4, 6] },
  { received: 28120, outstanding: 3140, growth: "21.6", points: [7300, 10700, 15700, 20800, 24300, 28120], invoices: [5, 6, 4, 7, 7, 4, 5] },
];
const digits = Array.from({ length: 10 }, (_, index) => index);

const RollingNumber = ({ value, prefix = "", suffix = "" }) => {
  const label = `${prefix}${value.toLocaleString("en-US")}${suffix}`;
  return (
    <span className="landing-rolling-number">
      <span className="landing-sr-only">{label}</span>
      <span className="landing-number-face" aria-hidden="true">
        {Array.from(label, (character, index) => /\d/.test(character) ? (
          <span className="landing-number-digit" key={index}>
            <span className="landing-number-reel" style={{ "--digit": character }}>
              {digits.map((digit) => <span key={digit}>{digit}</span>)}
            </span>
          </span>
        ) : <span className="landing-number-symbol" key={index}>{character}</span>)}
      </span>
    </span>
  );
};

const chartPoint = (value, index) => ({ x: 12 + index * 91.2, y: 164 - value / 32000 * 146 });
const chartPath = (values) => {
  const points = values.map(chartPoint);
  return points.reduce((path, point, index) => {
    if (!index) return `M ${point.x} ${point.y}`;
    const previous = points[index - 1];
    return `${path} C ${previous.x + 42} ${previous.y}, ${point.x - 42} ${point.y}, ${point.x} ${point.y}`;
  }, "");
};
const initialPath = chartPath(snapshots[0].points);

const RevenueChart = ({ points, running }) => {
  const lineRef = useRef(null);
  const areaRef = useRef(null);
  const dotRef = useRef(null);
  const currentPoints = useRef(snapshots[0].points);

  // Stop drawing before navigation clears the SVG refs.
  useLayoutEffect(() => {
    const draw = (values) => {
      const path = chartPath(values);
      lineRef.current.setAttribute("d", path);
      areaRef.current.setAttribute("d", `${path} L 468 170 L 12 170 Z`);
      dotRef.current.setAttribute("cy", chartPoint(values[5], 5).y);
      currentPoints.current = values;
    };
    if (!running) {
      draw(points);
      return undefined;
    }
    const from = currentPoints.current;
    if (from.every((value, index) => value === points[index])) return undefined;
    let frame;
    let started;
    const tick = (now) => {
      if (started === undefined) started = now;
      const progress = Math.min((now - started) / 1600, 1);
      const eased = progress < 0.5 ? 4 * progress ** 3 : 1 - (-2 * progress + 2) ** 3 / 2;
      draw(points.map((value, index) => from[index] + (value - from[index]) * eased));
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [points, running]);

  return (
    <div className="landing-revenue-chart" aria-hidden="true">
      <svg viewBox="0 0 480 180" preserveAspectRatio="none" fill="none">
        <defs>
          <linearGradient id="landing-revenue-fill" x1="0" y1="0" x2="0" y2="1">
            <stop stopColor="#8C7AF4" stopOpacity=".25" />
            <stop offset="1" stopColor="#8C7AF4" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="landing-revenue-stroke" x1="0" y1="0" x2="1" y2="0">
            <stop stopColor="#6C5CE7" /><stop offset="1" stopColor="#C6BEFF" />
          </linearGradient>
        </defs>
        {[24, 72, 120, 168].map((y) => <path key={y} d={`M 12 ${y} H 468`} className="landing-chart-gridline" />)}
        <path ref={areaRef} d={`${initialPath} L 468 170 L 12 170 Z`} fill="url(#landing-revenue-fill)" className="landing-chart-area" />
        <path d={chartPath([4200, 7500, 8800, 10700, 13700, 17300])} className="landing-chart-comparison" />
        <path ref={lineRef} d={initialPath} className="landing-chart-line" />
        <circle ref={dotRef} cx="468" cy={chartPoint(snapshots[0].received, 5).y} r="4" className="landing-chart-dot" />
      </svg>
      <div className="landing-chart-axis"><span>Day 1</span><span>10</span><span>20</span><span>30</span></div>
    </div>
  );
};

const MetricsShowcase = () => {
  const panelRef = useRef(null);
  const reducedMotion = usePrefersReducedMotion();
  const [visible, setVisible] = useState(false);
  const [tabVisible, setTabVisible] = useState(() => !document.hidden);
  const [snapshotIndex, setSnapshotIndex] = useState(0);
  const running = !reducedMotion && visible && tabVisible;
  const snapshot = snapshots[snapshotIndex];
  const collected = Math.round(snapshot.received / (snapshot.received + snapshot.outstanding) * 100);
  const invoices = snapshot.invoices.reduce((sum, value) => sum + value, 0);

  useEffect(() => {
    const observer = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting && entry.intersectionRatio >= 0.2), { threshold: 0.2 });
    observer.observe(panelRef.current);
    const onVisibilityChange = () => setTabVisible(!document.hidden);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      observer.disconnect();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);

  useEffect(() => {
    if (!running) return undefined;
    const advance = () => setSnapshotIndex((index) => (index + 1) % snapshots.length);
    // Start on the first visible frame instead of waiting for the repeating interval.
    const firstFrame = requestAnimationFrame(advance);
    const interval = setInterval(advance, 4600);
    return () => {
      cancelAnimationFrame(firstFrame);
      clearInterval(interval);
    };
  }, [running]);

  return (
    <figure className="landing-metrics" ref={panelRef} data-running={running} aria-labelledby="landing-metrics-title">
      <figcaption className="landing-metrics-caption"><span id="landing-metrics-title"><span /> YOUR BUSINESS, IN VIEW</span></figcaption>
      <section className="landing-revenue-card" aria-label="Sample payments received">
        <div className="landing-metric-heading"><h2>Payments received</h2><span>Last 30 days</span></div>
        <div className="landing-revenue-total">
          <strong><RollingNumber value={snapshot.received} prefix="$" /></strong>
          <span className="landing-metric-growth"><FiArrowUpRight aria-hidden="true" />{snapshot.growth}%</span>
        </div>
        <RevenueChart points={snapshot.points} running={running} />
        <div className="landing-chart-legend"><span><i />Received</span><span><i />Previous period</span></div>
      </section>

      <div className="landing-metrics-secondary">
        <section className="landing-mini-metric" aria-label="Sample invoices sent">
          <div className="landing-mini-heading"><FiFileText aria-hidden="true" /><h3>Invoices sent</h3></div>
          <div className="landing-invoice-metric"><strong><RollingNumber value={invoices} /></strong><span>Last 7 days</span></div>
          <div className="landing-metric-bars" aria-hidden="true">
            {snapshot.invoices.map((count, index) => <span className="landing-bar-column" key={index}><span className="landing-bar-track"><span style={{ "--bar-scale": count / 8, "--bar-delay": `${index * 45}ms` }} /></span><span>{["M", "T", "W", "T", "F", "S", "S"][index]}</span></span>)}
          </div>
        </section>
        <section className="landing-mini-metric" aria-label="Sample payment status">
          <div className="landing-mini-heading"><FiPieChart aria-hidden="true" /><h3>Payment status</h3></div>
          <div className="landing-collection-metric">
            <div className="landing-metric-ring">
              <svg viewBox="0 0 100 100" aria-hidden="true"><circle cx="50" cy="50" r="41" className="landing-ring-track" /><circle cx="50" cy="50" r="41" pathLength="100" strokeDasharray={`${collected} 100`} className="landing-ring-value" /></svg>
              <strong><RollingNumber value={collected} suffix="%" /></strong>
              <span>Collected</span>
            </div>
            <div className="landing-collection-detail"><strong><RollingNumber value={snapshot.outstanding} prefix="$" /></strong><small>Outstanding</small></div>
          </div>
        </section>
      </div>
    </figure>
  );
};

export default MetricsShowcase;
