import { useMemo } from "react";

// A pattern in the shape of a UPI QR code, for the homepage's samples: the
// three finder squares and a scatter of cells that is the same for a seed
// every time, and that no reader resolves to anything.
const CELLS = 25;

const pattern = (seed) => {
  const taken = new Set();
  const finder = (left, top) => {
    for (let y = 0; y < 7; y += 1) {
      for (let x = 0; x < 7; x += 1) {
        const ring = x === 0 || x === 6 || y === 0 || y === 6;
        const eye = x >= 2 && x <= 4 && y >= 2 && y <= 4;
        if (ring || eye) taken.add(`${left + x},${top + y}`);
      }
    }
  };
  finder(0, 0);
  finder(CELLS - 7, 0);
  finder(0, CELLS - 7);
  let state = seed;
  const next = () => {
    state = (state * 1103515245 + 12345) % 2147483648;
    return state / 2147483648;
  };
  for (let y = 0; y < CELLS; y += 1) {
    for (let x = 0; x < CELLS; x += 1) {
      const corner =
        (x < 8 && y < 8) ||
        (x >= CELLS - 8 && y < 8) ||
        (x < 8 && y >= CELLS - 8);
      if (!corner && next() < 0.46) taken.add(`${x},${y}`);
    }
  }
  return [...taken]
    .map((cell) => cell.split(","))
    .map(([x, y]) => `M${x} ${y}h1v1h-1z`)
    .join("");
};

// Size it with a class, such as size-[150px]. A pill on its corner says
// it is a sample, so nobody points a phone at it.
const QrPattern = ({ seed = 7, className = "" }) => {
  const cells = useMemo(() => pattern(seed), [seed]);
  return (
    <span className="relative block w-fit shrink-0">
      <svg
        className={`block rounded-[10px] bg-white ${className}`}
        viewBox={`-1 -1 ${CELLS + 2} ${CELLS + 2}`}
        shapeRendering="crispEdges"
        aria-hidden="true"
        focusable="false"
      >
        <path className="fill-spot-ink" d={cells} />
      </svg>
      <span className="absolute bottom-1.5 right-1.5 rounded-full bg-spot-night/85 px-1.5 py-0.5 text-[9px] font-medium uppercase leading-[1.3] tracking-[0.08em] text-white">
        Sample
      </span>
    </span>
  );
};

export default QrPattern;
