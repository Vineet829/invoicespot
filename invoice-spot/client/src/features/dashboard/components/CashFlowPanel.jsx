import { memo, useEffect, useMemo, useRef, useState } from "react";
import useWidth from "../../../hooks/useWidth";
import { sectionTitle } from "../../../components/workspace/pastel";
import {
  axisLabel,
  bucketLabel,
  bucketRange,
  cashFlowScale,
  formatMoney,
  labelStep,
  showsLabel,
} from "../dashboardModel";
import { overviewPanel } from "./DashboardPanels";

// The width of the axis figures beside the chart, and of the hover card.
const AXIS = 52;
const CARD = 190;
// What each part of the period is called, by the chart's step.
const PARTS = {
  day: ["Day", "day"],
  week: ["Week", "week"],
  month: ["Month", "month"],
};

const sum = (trend, key) =>
  trend.reduce((total, bucket) => total + (Number(bucket[key]) || 0), 0);
const net = (bucket) =>
  (Number(bucket.moneyIn) || 0) - (Number(bucket.moneyOut) || 0);

// The same figures as the chart, for screen readers. It doesn't change as
// the pointer or focus moves over the chart. It is hidden inside a box that
// clips it: a table is never narrower than its widest row, so hidden itself
// it would still widen the page on a small phone.
const FiguresTable = memo(({ parts, part, partName, currency }) => (
  <div className="sr-only">
    <table>
      <caption>{`Cash flow each ${partName}, ${currency}`}</caption>
      <thead>
        <tr>
          <th scope="col">{part}</th>
          <th scope="col">Money in</th>
          <th scope="col">Money out</th>
          <th scope="col">Net</th>
        </tr>
      </thead>
      <tbody>
        {parts.map((figures) => (
          <tr key={figures.from}>
            <th scope="row">{figures.range}</th>
            <td>{figures.moneyIn}</td>
            <td>{figures.moneyOut}</td>
            <td>{figures.net}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
));

// Money in and out of the business in each part of the period: money in
// rises above the zero line, money out hangs below it, and the dark line is
// what was left. Hovering a column, or moving through them with the arrow
// keys, shows its figures, until Escape hides them; a table gives screen
// readers the same figures.
const CashFlowPanel = ({ snapshot }) => {
  const { trend, currency } = snapshot;
  const step = snapshot.period.step;
  const [part, partName] = PARTS[step] || PARTS.week;
  const [measureRef, width] = useWidth(720);
  const columns = useRef([]);
  const [hovered, setHovered] = useState(null);
  const [focused, setFocused] = useState(null);
  const [dismissed, setDismissed] = useState(false);
  const [stop, setStop] = useState(null);
  const amount = (value) => formatMoney(value, currency);
  // Everything that depends only on the figures is worked out once for
  // them, not again as the pointer or focus moves.
  const { scale, parts, points, moneyIn, moneyOut } = useMemo(() => {
    const chartScale = cashFlowScale(trend);
    const format = (value) => formatMoney(value, currency);
    return {
      scale: chartScale,
      parts: trend.map((bucket) => ({
        from: bucket.from,
        range: bucketRange(bucket, step),
        label: bucketLabel(bucket, step),
        moneyIn: format(bucket.moneyIn),
        moneyOut: format(bucket.moneyOut),
        net: format(net(bucket)),
      })),
      points: chartScale
        ? trend.map((bucket, index) => ({
            x: ((index + 0.5) / trend.length) * 100,
            y: chartScale.at(net(bucket)),
          }))
        : [],
      moneyIn: sum(trend, "moneyIn"),
      moneyOut: sum(trend, "moneyOut"),
    };
  }, [trend, step, currency]);
  const plotWidth = Math.max(width - AXIS, 0);
  const every = labelStep(trend.length, plotWidth, step === "month" ? 36 : 46);
  // Every net figure gets a dot while the columns have room for one.
  const dotted = plotWidth / trend.length >= 18;
  const active = hovered ?? focused;
  const shown = active != null && active < trend.length ? active : null;
  const carded = dismissed ? null : shown;
  // One column at a time is in the tab order; the arrow keys move along.
  const tabStop = Math.min(stop ?? trend.length - 1, trend.length - 1);
  // Escape hides the card without moving the pointer or focus.
  useEffect(() => {
    if (carded == null) return undefined;
    const hide = (event) => {
      if (event.key === "Escape") setDismissed(true);
    };
    document.addEventListener("keydown", hide);
    return () => document.removeEventListener("keydown", hide);
  }, [carded]);
  const showColumn = (setter, index) => {
    setter(index);
    setDismissed(false);
  };
  // The hover card sits beside its column, clear of the column and its dot,
  // on whichever side has room; on a narrow chart, above the column.
  const cardLeft = (index) => {
    const center = ((index + 0.5) / trend.length) * plotWidth;
    if (center + 20 + CARD <= plotWidth) return center + 20;
    if (center - 20 - CARD >= 0) return center - 20 - CARD;
    return Math.min(Math.max(center - CARD / 2, 0), plotWidth - CARD);
  };
  const move = (event, index) => {
    const target = {
      ArrowLeft: index - 1,
      ArrowRight: index + 1,
      Home: 0,
      End: trend.length - 1,
    }[event.key];
    if (target === undefined) return;
    event.preventDefault();
    const next = Math.min(Math.max(target, 0), trend.length - 1);
    setStop(next);
    columns.current[next]?.focus();
  };

  return (
    <section
      className={`${overviewPanel} [--rise-delay:160ms]`}
      aria-labelledby="cash-flow-title"
    >
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <h2 id="cash-flow-title" className={sectionTitle}>
          Cash flow
        </h2>
        <ul className="m-0 flex list-none flex-wrap items-center gap-x-4 gap-y-1 p-0 text-[12.5px] text-spot-body">
          <li className="flex items-center gap-[7px]">
            <span
              className="size-3 rounded-[3px] bg-spot-violet"
              aria-hidden="true"
            />
            Money in
          </li>
          <li className="flex items-center gap-[7px]">
            <span
              className="size-3 rounded-[3px] bg-flow-out"
              aria-hidden="true"
            />
            Money out
          </li>
          <li className="flex items-center gap-[7px]">
            <span className="relative h-0.5 w-5 bg-flow-net" aria-hidden="true">
              <span className="absolute -top-[3px] left-1.5 size-2 rounded-full bg-flow-net" />
            </span>
            Net
          </li>
        </ul>
      </div>
      <div className="flex flex-wrap items-baseline justify-between gap-x-[18px] gap-y-1">
        <dl className="flex flex-wrap items-baseline gap-x-[18px] gap-y-1 text-[13px] text-spot-body">
          {[
            ["In", moneyIn],
            ["Out", moneyOut],
            ["Net", moneyIn - moneyOut],
          ].map(([term, value]) => (
            <div className="flex items-baseline gap-1.5" key={term}>
              <dt>{term}</dt>
              <dd className="font-display text-[20px] font-light tracking-[-0.02em] text-spot-ink">
                {amount(value)}
              </dd>
            </div>
          ))}
        </dl>
        {scale && !moneyOut && (
          <p className="text-[12.5px] text-spot-muted">
            No expenses recorded in this period
          </p>
        )}
      </div>
      <div ref={measureRef} className="min-w-0">
        {scale ? (
          // A new period or currency draws the chart afresh; the panel
          // around it stays.
          <div
            key={`${snapshot.period.key}-${currency}`}
            className="grid grid-cols-[52px_minmax(0,1fr)] grid-rows-[272px_22px] max-[700px]:grid-rows-[200px_22px]"
          >
            <div
              className="relative text-[11.5px] tabular-nums text-spot-muted"
              aria-hidden="true"
            >
              {scale.ticks.map((tick) => (
                <span
                  className="absolute right-2.5 -translate-y-1/2 whitespace-nowrap"
                  key={tick}
                  style={{ top: `${scale.at(tick)}%` }}
                >
                  {axisLabel(tick, scale.ticks, currency)}
                </span>
              ))}
            </div>
            <div className="relative" onMouseLeave={() => setHovered(null)}>
              {scale.ticks.map((tick) => (
                <span
                  className={`absolute inset-x-0 border-t ${tick === 0 ? "border-[#2327354d]" : "border-[#23273512]"}`}
                  key={tick}
                  style={{ top: `${scale.at(tick)}%` }}
                  aria-hidden="true"
                />
              ))}
              <div
                role="group"
                aria-label={`Cash flow each ${partName}`}
                className="absolute inset-0 grid"
                style={{
                  gridTemplateColumns: `repeat(${trend.length}, minmax(0, 1fr))`,
                }}
              >
                {trend.map((bucket, index) => {
                  const muted = shown != null && shown !== index;
                  return (
                    <div
                      key={bucket.from}
                      ref={(element) => {
                        columns.current[index] = element;
                      }}
                      role="img"
                      aria-label={`${parts[index].range}: money in ${parts[index].moneyIn}, money out ${parts[index].moneyOut}, net ${parts[index].net}`}
                      tabIndex={index === tabStop ? 0 : -1}
                      className={`relative rounded-lg transition-colors focus-visible:outline-offset-[-2px] ${shown === index ? "bg-spot-violet/[.06]" : ""}`}
                      onMouseEnter={() => showColumn(setHovered, index)}
                      onFocus={() => {
                        showColumn(setFocused, index);
                        setStop(index);
                      }}
                      onBlur={() => setFocused(null)}
                      onKeyDown={(event) => move(event, index)}
                    >
                      {Number(bucket.moneyIn) > 0 && (
                        <span
                          className={`absolute inset-x-0 mx-auto w-[min(24px,56%)] origin-bottom animate-ov-column rounded-t-[4px] bg-spot-violet transition-opacity ${muted ? "opacity-40" : ""}`}
                          style={{
                            bottom: `${100 - scale.at(0)}%`,
                            height: `${(bucket.moneyIn / scale.span) * 100}%`,
                            "--column-delay": `${300 + index * 30}ms`,
                          }}
                        />
                      )}
                      {Number(bucket.moneyOut) > 0 && (
                        <span
                          className={`absolute inset-x-0 mx-auto w-[min(24px,56%)] origin-top animate-ov-column rounded-b-[4px] bg-flow-out transition-opacity ${muted ? "opacity-40" : ""}`}
                          style={{
                            top: `${scale.at(0)}%`,
                            height: `${(bucket.moneyOut / scale.span) * 100}%`,
                            "--column-delay": `${300 + index * 30}ms`,
                          }}
                        />
                      )}
                    </div>
                  );
                })}
              </div>
              <svg
                className="pointer-events-none absolute inset-0 size-full overflow-visible"
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                aria-hidden="true"
                focusable="false"
              >
                <polyline
                  className="stroke-flow-net"
                  points={points.map(({ x, y }) => `${x},${y}`).join(" ")}
                  fill="none"
                  strokeWidth="2"
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
              <div
                className="pointer-events-none absolute inset-0"
                aria-hidden="true"
              >
                {points.map(
                  (point, index) =>
                    (dotted || index === shown) && (
                      <span
                        className="absolute -ml-[6.5px] -mt-[6.5px] size-[13px] rounded-full border-2 border-white bg-flow-net"
                        key={index}
                        style={{ left: `${point.x}%`, top: `${point.y}%` }}
                      />
                    ),
                )}
              </div>
              {carded != null && (
                <div
                  className="pointer-events-none absolute top-1 z-[2] w-[190px] rounded-[14px] bg-white px-3 py-2.5 text-[12.5px] leading-[1.45] text-spot-ink shadow-float"
                  style={{ left: cardLeft(carded) }}
                  aria-hidden="true"
                >
                  <p className="mb-1.5 font-medium">{parts[carded].range}</p>
                  {[
                    ["Money in", "bg-spot-violet", parts[carded].moneyIn],
                    ["Money out", "bg-flow-out", parts[carded].moneyOut],
                  ].map(([label, swatch, value]) => (
                    <p
                      className="mt-0.5 flex justify-between gap-3"
                      key={label}
                    >
                      <span className="flex items-center gap-1.5">
                        <span className={`size-[9px] rounded-sm ${swatch}`} />
                        {label}
                      </span>
                      {value}
                    </p>
                  ))}
                  <p className="mt-1.5 flex justify-between gap-3 border-t border-[#23273514] pt-1.5 font-medium">
                    <span>Net</span>
                    {parts[carded].net}
                  </p>
                </div>
              )}
            </div>
            <span aria-hidden="true" />
            <div
              className="grid items-end"
              style={{
                gridTemplateColumns: `repeat(${trend.length}, minmax(0, 1fr))`,
              }}
              aria-hidden="true"
            >
              {parts.map((figures, index) => (
                <span
                  className="flex justify-center whitespace-nowrap text-[11.5px] text-spot-muted"
                  key={figures.from}
                >
                  {showsLabel(index, trend.length, every) && figures.label}
                </span>
              ))}
            </div>
          </div>
        ) : (
          <p className="grid h-[294px] place-items-center text-center text-[13px] text-spot-muted max-[700px]:h-[222px]">
            Nothing came in or went out in this period.
          </p>
        )}
      </div>
      <FiguresTable
        parts={parts}
        part={part}
        partName={partName}
        currency={currency}
      />
    </section>
  );
};

export default CashFlowPanel;
