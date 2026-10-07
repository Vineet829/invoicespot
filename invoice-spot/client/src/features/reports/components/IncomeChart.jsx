import { useRef, useState } from "react";
import useWidth from "../../../hooks/useWidth";
import { axisLabel, formatMoney } from "../../dashboard/dashboardModel";
import { axisTicks, monthName, monthYear } from "../reportModel";

const AXIS = 52;
const CARD = 190;

// The chart's two columns, for its legend too.
export const SERIES = [
  { key: "billed", label: "Billed", column: "bg-card-blue-bar" },
  { key: "received", label: "Received", column: "bg-card-lilac-bar" },
];

const amount = (month, key) => Number(month[key]) || 0;

// Billed and received side by side for each month, drawn as the overview
// draws its cash flow: a month's figures show on hover or focus while the
// other months fade back, and the keys move between months. The table
// under the chart has the same figures.
const IncomeChart = ({ months, currency }) => {
  const [measureRef, width] = useWidth(720);
  const columns = useRef([]);
  const [hovered, setHovered] = useState(null);
  const [focused, setFocused] = useState(null);
  const [stop, setStop] = useState(null);
  const ticks = axisTicks(
    Math.max(
      0,
      ...months.flatMap((month) => SERIES.map(({ key }) => amount(month, key))),
    ),
  ).reverse();
  const top = ticks[0] || 1;
  const at = (value) => (1 - value / top) * 100;
  const plotWidth = Math.max(width - AXIS, 0);
  const every = plotWidth / months.length < 34 ? 2 : 1;
  const shown = hovered ?? focused;
  const tabStop = Math.min(stop ?? months.length - 1, months.length - 1);
  const cardLeft = (index) => {
    const center = ((index + 0.5) / months.length) * plotWidth;
    if (center + 24 + CARD <= plotWidth) return center + 24;
    if (center - 24 - CARD >= 0) return center - 24 - CARD;
    return Math.min(Math.max(center - CARD / 2, 0), plotWidth - CARD);
  };
  const move = (event, index) => {
    const target = {
      ArrowLeft: index - 1,
      ArrowRight: index + 1,
      Home: 0,
      End: months.length - 1,
    }[event.key];
    if (target === undefined) return;
    event.preventDefault();
    const next = Math.min(Math.max(target, 0), months.length - 1);
    setStop(next);
    columns.current[next]?.focus();
  };

  return (
    <div ref={measureRef} className="min-w-0">
      {/* A new period or currency draws the chart afresh, so its columns
          rise again. */}
      <div
        key={`${currency}-${months[0]?.month}-${months.length}`}
        className="grid grid-cols-[52px_minmax(0,1fr)] grid-rows-[272px_34px] max-[700px]:grid-rows-[200px_34px]"
      >
        <div
          className="relative text-[12.5px] tabular-nums text-spot-muted"
          aria-hidden="true"
        >
          {ticks.map((tick) => (
            <span
              className="absolute right-2.5 -translate-y-1/2 whitespace-nowrap"
              key={tick}
              style={{ top: `${at(tick)}%` }}
            >
              {axisLabel(tick, ticks, currency)}
            </span>
          ))}
        </div>
        <div className="relative" onMouseLeave={() => setHovered(null)}>
          {ticks.map((tick) => (
            <span
              className={`absolute inset-x-0 border-t ${tick === 0 ? "border-[#2327354d]" : "border-[#23273512]"}`}
              key={tick}
              style={{ top: `${at(tick)}%` }}
              aria-hidden="true"
            />
          ))}
          <div
            role="group"
            aria-label="Billed and received by month"
            className="absolute inset-0 grid"
            style={{
              gridTemplateColumns: `repeat(${months.length}, minmax(0, 1fr))`,
            }}
          >
            {months.map((month, index) => {
              const muted = shown != null && shown !== index;
              return (
                <div
                  key={month.month}
                  ref={(element) => {
                    columns.current[index] = element;
                  }}
                  role="img"
                  aria-label={`${monthYear(month.month)}: billed ${formatMoney(month.billed, currency)}, received ${formatMoney(month.received, currency)}`}
                  tabIndex={index === tabStop ? 0 : -1}
                  className={`relative flex items-end justify-center gap-1 rounded-lg transition-colors focus-visible:outline-offset-[-2px] ${shown === index ? "bg-spot-violet/[.06]" : ""}`}
                  onMouseEnter={() => setHovered(index)}
                  onFocus={() => {
                    setFocused(index);
                    setStop(index);
                  }}
                  onBlur={() => setFocused(null)}
                  onKeyDown={(event) => move(event, index)}
                >
                  {SERIES.map(({ key, column }) => (
                    <span
                      key={key}
                      className={`w-[min(24px,40%)] origin-bottom animate-ov-column rounded-t-[6px] transition-opacity ${column} ${muted ? "opacity-40" : ""}`}
                      style={{
                        height: `${100 - at(amount(month, key))}%`,
                        "--column-delay": `${250 + index * 40}ms`,
                      }}
                    />
                  ))}
                </div>
              );
            })}
          </div>
          {shown != null && (
            <div
              className="pointer-events-none absolute top-1 z-[2] w-[190px] rounded-[14px] bg-white px-3 py-2.5 text-[13px] leading-[1.45] text-spot-ink shadow-float"
              style={{ left: cardLeft(shown) }}
              aria-hidden="true"
            >
              <p className="mb-1.5 font-medium">
                {monthYear(months[shown].month)}
              </p>
              {SERIES.map(({ key, label, column }) => (
                <p className="mt-0.5 flex justify-between gap-3" key={key}>
                  <span className="flex items-center gap-1.5">
                    <span className={`size-[9px] rounded-sm ${column}`} />
                    {label}
                  </span>
                  {formatMoney(months[shown][key], currency)}
                </p>
              ))}
            </div>
          )}
        </div>
        <div aria-hidden="true" />
        <div
          className="grid text-[12.5px] text-spot-muted"
          style={{
            gridTemplateColumns: `repeat(${months.length}, minmax(0, 1fr))`,
          }}
          aria-hidden="true"
        >
          {months.map((month, index) => (
            <span
              key={month.month}
              className="flex flex-col items-center pt-2 leading-tight"
            >
              {index % every === 0 && monthName(month.month)}
              {(index === 0 || month.month.endsWith("-01")) && (
                <span className="text-[11.5px] text-spot-faint">
                  {month.month.slice(0, 4)}
                </span>
              )}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
};

export default IncomeChart;
