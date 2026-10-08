import { Fragment, useEffect, useState } from "react";
import { FiCheck } from "react-icons/fi";
import ClientPhone from "./ClientPhone";
import overview from "./screens/overview.webp";
import payments from "./screens/payments.webp";
import receivables from "./screens/receivables.webp";
import sales from "./screens/sales.webp";

// The four screens of the tour, taken from the sample business at
// 1440×720. That business bills in dollars with sales tax, so the notes
// say so. Each has where its pointer goes and what it highlights, as a
// share of the screenshot's width and height, and the note that pops up on
// the click.
export const SCREENS = [
  {
    name: "Overview",
    src: overview,
    alt: "The Overview: total receivables by age, billed with its tax, received and profit.",
    x: 44,
    y: 48,
    box: [37.5, 41, 15, 11],
    toast: "3 invoices overdue · $6,326.03",
  },
  {
    name: "Receivables",
    src: receivables,
    alt: "Receivables: overdue invoices by age, with a reminder button on each.",
    x: 93,
    y: 73,
    box: [21.5, 69.5, 76.5, 6.5],
    toast: "Reminder ready for INV-1026",
  },
  {
    name: "Sales",
    src: sales,
    alt: "Sales: what was billed, by type and by tax rate.",
    x: 80,
    y: 43,
    box: [60.5, 31, 37.5, 19.5],
    toast: "$4,654.08 sales tax at 7.25%",
  },
  {
    name: "Payments",
    src: payments,
    alt: "Payments: what came in by payment method, and who paid most.",
    x: 66,
    y: 59,
    box: [60.5, 55.5, 37.5, 6.5],
    toast: "Top payer: Quill & Co. · $11,490.77",
  },
];

// Where the pointer rests between screens, as a share of the screenshot.
const REST = { x: 84, y: 90 };
// A screen's step, in milliseconds from its start: the pointer sets off
// for its target, clicks, and the next screen follows.
export const MOVE_AT = 700;
export const CLICK_AT = 1900;
export const NEXT_AT = 4800;

const percent = (value) => `${value}%`;

// The product tour: the four screens cross-fading in a tilted frame, with
// a pointer that shows one thing on each, and the client's phone over the
// frame's corner. `running` plays it; a dot below picks a screen and stops
// it there, with that screen's highlight and note held still. With motion
// off it holds the first screen that way, with no pointer.
const Tour = ({ motion, running }) => {
  const [screen, setScreen] = useState(0);
  // 0: the pointer rests; 1: it is on its way; 2: it has clicked.
  const [phase, setPhase] = useState(motion ? 0 : 2);
  const [stopped, setStopped] = useState(false);
  const playing = running && !stopped;
  useEffect(() => {
    if (!playing) return undefined;
    setPhase(0);
    const timers = [
      setTimeout(() => setPhase(1), MOVE_AT),
      setTimeout(() => setPhase(2), CLICK_AT),
      setTimeout(
        () => setScreen((current) => (current + 1) % SCREENS.length),
        NEXT_AT,
      ),
    ];
    return () => timers.forEach(clearTimeout);
  }, [playing, screen]);
  const show = (index) => {
    setStopped(true);
    setScreen(index);
    setPhase(2);
  };

  const { x, y, box, toast, name } = SCREENS[screen];
  const [left, top, width, height] = box;
  const atTarget = phase > 0;
  return (
    <div
      className={`min-w-0 flex-[1.4_1_540px] [perspective:1800px] ${motion ? "animate-home-lift [--lift-delay:300ms]" : ""}`}
    >
      <div className="relative min-[861px]:[transform:rotateY(-9deg)_rotateX(5deg)] min-[861px]:[transform-style:preserve-3d]">
        <div className="relative rounded-[22px] border border-white/[.18] bg-white/10 p-2 shadow-[0_60px_120px_-40px_#000000bf,0_0_80px_-20px_#7c62d699]">
          {SCREENS.map((item, index) => (
            <img
              key={item.name}
              src={item.src}
              alt={item.alt}
              width={1440}
              height={720}
              loading={index === 0 ? "eager" : "lazy"}
              className={`block h-auto rounded-[15px] [transition:opacity_.8s_ease] ${index === 0 ? "relative w-full" : "absolute left-2 top-2 w-[calc(100%-16px)]"} ${index === screen ? "opacity-100" : "opacity-0"}`}
            />
          ))}
          <div
            className="pointer-events-none absolute inset-2"
            aria-hidden="true"
          >
            {phase === 2 && (
              <Fragment key={screen}>
                <span
                  className={`absolute rounded-[10px] border-2 border-spot-brand shadow-[0_0_0_6px_#7c62d62e] ${motion ? "animate-home-pop" : ""}`}
                  style={{
                    left: percent(left),
                    top: percent(top),
                    width: percent(width),
                    height: percent(height),
                  }}
                />
                {motion && (
                  <span
                    className="absolute size-[46px] animate-home-ripple rounded-full border-[3px] border-spot-brand"
                    style={{ left: percent(x), top: percent(y) }}
                  />
                )}
                <span
                  className={`absolute right-[3%] top-[4%] inline-flex items-center gap-2.5 rounded-[14px] bg-white px-3.5 py-2.5 text-[13.5px] font-medium text-spot-ink shadow-[0_20px_40px_-20px_#28225099] ${motion ? "animate-home-pop [--pop-delay:250ms]" : ""}`}
                >
                  <span className="grid size-[22px] place-items-center rounded-full bg-card-sage text-card-sage-icon [&>svg]:size-[13px] [&>svg]:stroke-[3]">
                    <FiCheck />
                  </span>
                  {toast}
                </span>
              </Fragment>
            )}
            {motion && (
              <svg
                className="absolute size-6 drop-shadow-[0_4px_6px_#0000004d] [transition:left_1.1s_cubic-bezier(.45,0,.2,1),top_1.1s_cubic-bezier(.45,0,.2,1)]"
                viewBox="0 0 24 24"
                style={{
                  left: percent(atTarget ? x : REST.x),
                  top: percent(atTarget ? y : REST.y),
                }}
              >
                <path
                  d="M5 3l14 8.2-6.3 1.5L10 19z"
                  fill="#ffffff"
                  stroke="#232735"
                  strokeWidth="1.6"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </div>
        </div>
        <ClientPhone motion={motion} />
      </div>
      <div className="mt-[26px] flex items-center justify-center gap-3.5">
        <div className="flex gap-1.5" role="group" aria-label="Product screens">
          {SCREENS.map((item, index) => (
            <button
              key={item.name}
              type="button"
              className={`relative h-2 rounded-full p-0 transition-[width,background-color] duration-300 before:absolute before:-inset-x-1 before:-inset-y-3 before:content-[''] ${index === screen ? "w-[26px] bg-white" : "w-2 bg-white/[.32] hover:bg-white/50"}`}
              aria-label={`Show ${item.name}`}
              aria-pressed={index === screen}
              onClick={() => show(index)}
            />
          ))}
        </div>
        {/* The name is announced once the visitor has picked a screen, not
            while the tour turns on its own. */}
        <span
          className="text-[14px] text-[#cfc8ea]"
          aria-live={stopped ? "polite" : "off"}
        >
          {name}
        </span>
      </div>
    </div>
  );
};

export default Tour;
