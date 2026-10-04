import { useState } from "react";
import useWidth from "../../../hooks/useWidth";
import {
  HoverCard,
  useHoverCard,
} from "../../dashboard/components/DashboardPanels";
import { hoverLabel, hoverValue } from "../../../components/workspace/pastel";
import { money } from "../../../components/workspace/Workspace";
import { axisTicks, compactAmount, monthName, monthYear } from "../reportModel";

const HEIGHT = 250;
const AXIS = 52;
const TOP = 10;
const LABELS = 40;

export const SERIES = [
  {
    key: "billed",
    label: "Billed",
    fill: "fill-card-blue-bar",
    dot: "bg-card-blue-bar",
  },
  {
    key: "received",
    label: "Received",
    fill: "fill-card-lilac-bar",
    dot: "bg-card-lilac-bar",
  },
];

// Billed and received side by side for each month. The same figures are in
// the table that follows the chart, so the chart itself is hidden from
// assistive technology.
const IncomeChart = ({ months, currency }) => {
  const [measureRef, width] = useWidth(720);
  const { containerRef, card, show, hide } = useHoverCard();
  const [active, setActive] = useState(null);
  const ticks = axisTicks(
    Math.max(
      ...months.flatMap((month) => [
        Number(month.billed),
        Number(month.received),
      ]),
    ),
  );
  const top = ticks.at(-1) || 1;
  const plotHeight = HEIGHT - TOP - LABELS;
  const slot = Math.max(width - AXIS, 0) / months.length;
  const bar = Math.max(3, Math.min(26, (slot - 12) / 2));
  const labelEvery = slot < 30 ? 2 : 1;
  const y = (value) => TOP + plotHeight - (Number(value) / top) * plotHeight;
  const hover = (index, event) => {
    const month = months[index];
    setActive(index);
    show(
      event,
      <>
        <strong className="mb-1.5 block text-[13px] font-medium text-spot-ink">
          {monthYear(month.month)}
        </strong>
        {SERIES.map((series) => (
          <span key={series.key} className="mt-1 block">
            <span className={hoverLabel}>
              <span
                className={`size-2.5 shrink-0 rounded-full ${series.dot}`}
                aria-hidden="true"
              />
              {series.label}
            </span>
            <span className={hoverValue}>
              {money(month[series.key], currency)}
            </span>
          </span>
        ))}
      </>,
    );
  };
  const leave = () => {
    setActive(null);
    hide();
  };
  return (
    <div className="relative" ref={containerRef} onMouseLeave={leave}>
      <div ref={measureRef}>
        <svg
          key={`${currency}-${months[0]?.month}-${months.length}`}
          className="block overflow-visible"
          width={width}
          height={HEIGHT}
          viewBox={`0 0 ${width} ${HEIGHT}`}
          aria-hidden="true"
          focusable="false"
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                className={
                  tick === 0 ? "stroke-[#23273533]" : "stroke-[#2327351a]"
                }
                x1={AXIS}
                x2={width}
                y1={y(tick)}
                y2={y(tick)}
                strokeDasharray={tick === 0 ? undefined : "3 5"}
              />
              <text
                className="fill-spot-faint text-[11px] tabular-nums"
                x={AXIS - 12}
                y={y(tick)}
                dy="0.32em"
                textAnchor="end"
              >
                {compactAmount(tick, currency)}
              </text>
            </g>
          ))}
          {months.map((month, index) => {
            const left = AXIS + index * slot;
            const center = left + slot / 2;
            const year = month.month.slice(0, 4);
            return (
              <g key={month.month} onMouseMove={(event) => hover(index, event)}>
                <rect
                  className={`transition-colors ${active === index ? "fill-spot-violet/[.06]" : "fill-transparent"}`}
                  x={left + 2}
                  y={TOP - 4}
                  width={Math.max(slot - 4, 0)}
                  height={plotHeight + 8}
                  rx="10"
                />
                {SERIES.map((series, position) => {
                  const height = (Number(month[series.key]) / top) * plotHeight;
                  return (
                    height > 0 && (
                      <rect
                        key={series.key}
                        className={`origin-bottom animate-ov-column [transform-box:fill-box] ${series.fill}`}
                        style={{ "--column-delay": `${250 + index * 45}ms` }}
                        x={position === 0 ? center - bar - 2 : center + 2}
                        y={TOP + plotHeight - height}
                        width={bar}
                        height={height}
                        rx={Math.min(5, bar / 2)}
                      />
                    )
                  );
                })}
                {index % labelEvery === 0 && (
                  <text
                    className="fill-spot-muted text-[11.5px]"
                    x={center}
                    y={TOP + plotHeight + 18}
                    textAnchor="middle"
                  >
                    {monthName(month.month)}
                  </text>
                )}
                {(index === 0 || month.month.endsWith("-01")) && (
                  <text
                    className="fill-spot-faint text-[10.5px] tabular-nums"
                    x={center}
                    y={TOP + plotHeight + 33}
                    textAnchor="middle"
                  >
                    {year}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
      </div>
      <HoverCard card={card} />
    </div>
  );
};

export default IncomeChart;
