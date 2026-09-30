import { useRef, useState } from "react";
import { FiAlertCircle, FiFileText, FiUsers } from "react-icons/fi";
import { getStatusRingRatio } from "../dashboardModel";
import {
  hoverLabel,
  hoverValue,
  panelNote,
  sectionTitle,
  skeleton,
} from "../../../components/workspace/pastel";

// A small card that follows the pointer inside a positioned container. It
// adds detail for mouse users; the same facts stay readable as text or as
// screen-reader text, so touch and keyboard users lose nothing.
export const useHoverCard = () => {
  const containerRef = useRef(null);
  const [card, setCard] = useState(null);
  const show = (event, content) => {
    const box = containerRef.current?.getBoundingClientRect();
    if (!box || !window.matchMedia("(hover: hover)").matches) return;
    const x = event.clientX - box.left;
    const y = event.clientY - box.top;
    setCard({
      x,
      y,
      flipX: x > box.width - 260,
      flipY: y > box.height - 150,
      content,
    });
  };
  const hide = () => setCard(null);
  return { containerRef, card, show, hide };
};

export const HoverCard = ({ card }) =>
  card ? (
    <div
      className={`pointer-events-none absolute z-[5] w-max max-w-[240px] animate-ov-fade rounded-[14px] border border-[#23273514] bg-white px-3.5 py-3 text-[13px] leading-[1.45] text-spot-ink shadow-float ${card.flipX ? "-translate-x-[calc(100%+14px)]" : "translate-x-3.5"} ${card.flipY ? "-translate-y-[calc(100%+14px)]" : "translate-y-3.5"}`}
      role="tooltip"
      style={{ left: card.x, top: card.y }}
    >
      {card.content}
    </div>
  ) : null;

const STATUS_DOTS = ["bg-spot-violet", "bg-spot-lilac", "bg-spot-coral"];
const RING_STROKES = [
  "stroke-spot-violet",
  "stroke-spot-lilac",
  "stroke-spot-coral",
];

// Each digit rolls into place once. The digits keep their final width from
// the start, so nothing around a figure moves while it animates.
const NumberCharacters = ({ text, offset = 0 }) =>
  [...text].map((character, index) =>
    /\d/.test(character) ? (
      <span
        className="inline-block align-baseline [clip-path:inset(0_-0.25em)]"
        style={{ "--digit-delay": `${Math.min(index + offset, 10) * 55}ms` }}
        key={index}
      >
        <span
          className="relative block animate-ov-roll leading-none before:absolute before:bottom-full before:left-0 before:w-full before:whitespace-pre before:leading-[inherit] before:content-[attr(data-roll)]"
          data-roll={Array.from(
            { length: 4 },
            (_, step) => (Number(character) + step + 6) % 10,
          ).join("\n")}
        >
          {character}
        </span>
      </span>
    ) : (
      <span key={index}>{character}</span>
    ),
  );

// The real value is available to assistive technology immediately; the
// rolling digits are decorative.
export const DashboardNumber = ({
  value,
  className = "",
  decimalClass = "",
}) => {
  const text = String(value);
  const hasDecimals = /\.\d{2}$/.test(text);
  return (
    <span
      className={`block overflow-clip font-display font-extralight leading-none tracking-[-0.05em] [overflow-clip-margin:3px] ${className}`}
    >
      <span className="sr-only">{text}</span>
      <span className="whitespace-nowrap" aria-hidden="true" key={text}>
        <NumberCharacters text={hasDecimals ? text.slice(0, -3) : text} />
        {hasDecimals && (
          <span className={`text-[0.56em] tracking-[-0.02em] ${decimalClass}`}>
            <NumberCharacters text={text.slice(-3)} offset={text.length - 3} />
          </span>
        )}
      </span>
    </span>
  );
};

// Loading placeholders keep every panel in place and never show figures.
const Skeleton = ({ className }) => (
  <span className={`${skeleton} ${className}`} aria-hidden="true" />
);

export const OverviewTotals = ({
  customerCount,
  documentCount,
  customersLoading,
  documentsLoading,
  customersError,
}) => (
  <dl
    className="flex shrink-0 items-end max-[1359px]:self-center max-[700px]:w-full"
    aria-label="Customer and document totals"
  >
    {[
      {
        label: "Clients",
        value: customerCount,
        loading: customersLoading,
        error: customersError,
        icon: FiUsers,
        iconColor: "[&>svg]:text-spot-violet",
      },
      {
        label: "Documents",
        value: documentCount,
        loading: documentsLoading,
        icon: FiFileText,
        iconColor: "[&>svg]:text-spot-blue",
      },
    ].map(({ label, value, loading, error, icon: Icon, iconColor }) => (
      <div
        className="relative px-8 first:border-r first:border-[#2327351a] last:pr-0 max-[1359px]:px-[18px] max-[1359px]:last:pr-0 max-[700px]:flex-1 max-[700px]:px-6 max-[700px]:first:pl-0 max-[700px]:last:pr-0"
        key={label}
      >
        <dt
          className={`flex items-center gap-2 text-[13px] text-spot-body [&>svg]:size-[15px] [&>svg]:stroke-[1.5] ${iconColor}`}
        >
          <Icon aria-hidden="true" />
          {label}
        </dt>
        <dd className="mt-1.5 text-[46px] compact:text-[40px] max-[1359px]:text-[38px] max-[700px]:text-[36px]">
          {loading ? (
            <>
              <span className="sr-only">Loading</span>
              <Skeleton className="h-[46px] w-12 rounded-md" />
            </>
          ) : (
            <DashboardNumber
              value={error || value == null ? "—" : value.toLocaleString("en")}
            />
          )}
          {error && (
            <span className="mt-1 block text-[11px]">Count unavailable</span>
          )}
        </dd>
      </div>
    ))}
  </dl>
);

// The financial summary's three pastel cards.
const CARD_TONES = {
  blue: {
    card: "bg-card-blue",
    label: "text-card-blue-label",
    icon: "text-card-blue-icon",
    ink: "text-card-blue-ink",
    muted: "text-card-blue-muted",
    decimal: "text-card-blue-decimal",
    track: "bg-card-blue-track",
    bar: "bg-card-blue-bar",
  },
  lilac: {
    card: "bg-card-lilac",
    label: "text-card-lilac-label",
    icon: "text-card-lilac-icon",
    ink: "text-card-lilac-ink",
    muted: "text-card-lilac-muted",
    decimal: "text-card-lilac-decimal",
    track: "bg-card-lilac-track",
    bar: "bg-card-lilac-bar",
  },
  peach: {
    card: "bg-card-peach",
    label: "text-card-peach-label",
    icon: "text-card-peach-icon",
    ink: "text-card-peach-ink",
    muted: "text-card-peach-muted",
    decimal: "text-card-peach-decimal",
    track: "bg-card-peach-track",
    bar: "bg-card-peach-bar",
  },
};

export const financePanel =
  "grid min-w-0 animate-ov-rise grid-rows-[36px_minmax(0,1fr)] gap-[18px] rounded-[30px] border border-white/90 bg-white/[.66] p-6 shadow-panel backdrop-blur-[16px] [--rise-delay:80ms] compact:grid-rows-[30px_minmax(0,1fr)] compact:gap-2 compact:px-[18px] compact:py-3.5 max-[1359px]:grid-rows-[auto_minmax(0,1fr)] max-[1359px]:p-5 max-[700px]:gap-4 max-[700px]:rounded-3xl max-[700px]:p-[18px]";
export const financePanelHeading =
  "flex min-w-0 items-center justify-between gap-4 px-1 max-[1359px]:flex-wrap max-[1359px]:gap-x-4 max-[1359px]:gap-y-1 max-[700px]:p-0";
export const financeCards =
  "grid min-h-0 grid-cols-3 gap-4 max-[1359px]:gap-3 max-[700px]:grid-cols-1";
const financeCard = (tone) =>
  `flex min-w-0 flex-col justify-between gap-[18px] rounded-[22px] p-6 [container-name:finance-card] [container-type:inline-size] compact:gap-2.5 compact:px-3.5 compact:py-[18px] max-[1359px]:p-[18px] max-[700px]:min-h-[240px] max-[700px]:p-[22px] ${CARD_TONES[tone].card}`;

export const FinancialCard = ({
  label,
  value,
  detail,
  icon: Icon,
  tone,
  fill,
}) => {
  const colors = CARD_TONES[tone];
  return (
    <article
      className={financeCard(tone)}
      style={{ "--chars": String(value).length }}
    >
      <header className="flex items-center justify-between gap-2.5">
        <h3
          className={`rounded-full bg-white/70 px-4 py-2 font-ui text-[15px] font-normal [@container_finance-card_(min-width:162px)]:text-[16px] ${colors.label}`}
        >
          {label}
        </h3>
        <span
          className={`grid size-[42px] shrink-0 place-items-center rounded-full bg-white/60 [&>svg]:size-5 [&>svg]:stroke-[1.6] [@container_finance-card_(max-width:210px)]:hidden ${colors.icon}`}
          aria-hidden="true"
        >
          <Icon />
        </span>
      </header>
      <div className={colors.ink}>
        <DashboardNumber
          value={value}
          className="[font-size:min(56px,calc(100cqi_/_(var(--chars)_*_0.5)))]"
          decimalClass={colors.decimal}
        />
        {/* When a description wraps, every card reserves two lines so the
            three figures stay level. */}
        <p
          className={`mt-3 text-[15px] [@container_finance-card_(max-width:270px)]:min-h-[2lh] [@container_finance-card_(min-width:162px)]:text-[16px] ${colors.muted}`}
        >
          {detail}
        </p>
      </div>
      {fill != null && (
        <div
          className={`h-2.5 shrink-0 overflow-hidden rounded-full ${colors.track}`}
          aria-hidden="true"
        >
          <span
            className={`block h-full origin-left animate-ov-bar rounded-[inherit] ${colors.bar}`}
            style={{ width: `${fill * 100}%` }}
          />
        </div>
      )}
    </article>
  );
};

// One ring per status, each drawn as that status's share of all documents.
// Overdue overlaps these statuses, so it is never drawn as a ring.
export const StatusRings = ({
  paid,
  partial,
  unpaid,
  total,
  active,
  onHover,
  onLeave,
}) => (
  <svg
    className="mx-auto block h-auto w-[260px] max-w-full shrink-0 overflow-visible min-[1360px]:w-[clamp(160px,calc(100cqh_-_var(--overview-status-chrome,300px)),260px)] max-[700px]:w-[260px]"
    viewBox="0 0 260 260"
    width="260"
    height="260"
    aria-hidden="true"
    focusable="false"
  >
    {[paid, partial, unpaid].map((count, index) => {
      const ratio = getStatusRingRatio(count, total);
      return (
        <g
          className={`fill-none stroke-[18px] transition-opacity duration-200 ${active != null && active !== index ? "opacity-25" : ""}`}
          key={index}
          onMouseMove={onHover && ((event) => onHover(index, event))}
          onMouseLeave={onLeave}
        >
          <circle
            className="stroke-[#23273512]"
            cx="130"
            cy="130"
            r={118 - index * 26}
          />
          {ratio > 0 && (
            <circle
              className={`animate-ov-ring [stroke-dasharray:100_100] [stroke-dashoffset:var(--ring-offset)] [stroke-linecap:round] ${RING_STROKES[index]}`}
              cx="130"
              cy="130"
              r={118 - index * 26}
              pathLength="100"
              transform="rotate(-90 130 130)"
              style={{
                "--ring-offset": 100 - ratio * 100,
                "--ring-delay": `${500 + index * 150}ms`,
              }}
            />
          )}
        </g>
      );
    })}
  </svg>
);

export const statusPanel =
  "flex min-h-0 min-w-0 animate-ov-rise flex-col gap-5 rounded-[30px] border border-white/85 bg-white/50 p-[26px] shadow-panel-soft backdrop-blur-[16px] [--rise-delay:240ms] compact:gap-3.5 compact:p-[18px] max-[700px]:gap-4 max-[700px]:rounded-3xl max-[700px]:p-[18px]";
// The card is a size container on desktop so the rings can shrink when
// height runs out; compact windows reserve less room for the legend.
export const statusCard =
  "relative flex min-h-0 flex-1 flex-col justify-between gap-4 rounded-3xl border border-white/95 bg-white/[.72] px-[26px] py-6 min-[1360px]:[container-type:size] compact:gap-2.5 compact:px-5 compact:py-[18px] compact:[--overview-status-chrome:255px] max-[1359px]:grid max-[1359px]:grid-cols-[260px_minmax(0,1fr)] max-[1359px]:items-center max-[1359px]:gap-x-8 max-[1359px]:gap-y-5 max-[700px]:flex max-[700px]:gap-7 max-[700px]:rounded-[20px] max-[700px]:px-5 max-[700px]:py-[22px] max-[700px]:[&>:not(svg)]:w-full";
export const statusLegend = "max-[1359px]:col-start-2 max-[1359px]:row-start-2";
const statusRow = (index) =>
  `flex items-center justify-between gap-4 py-3 transition-opacity duration-200 compact:py-2 ${index > 0 ? "border-t border-[#23273514]" : ""}`;
const overdueBar =
  "flex min-h-[82px] items-center gap-4 rounded-[22px] border border-white/90 bg-overdue/85 px-5 py-[18px] compact:min-h-0 compact:px-4 compact:py-3 max-[700px]:gap-3 max-[700px]:p-4";

export const DocumentStatus = ({ snapshot, total }) => {
  const [active, setActive] = useState(null);
  const { containerRef, card, show, hide } = useHoverCard();
  const statuses = [
    ["Paid", "Settled in full", snapshot.paid],
    ["Partially paid", "Part of the amount received", snapshot.partial],
    ["Unpaid", "Nothing received yet", snapshot.unpaid],
  ];
  const hoverRing = (index, event) => {
    const [label, detail, value] = statuses[index];
    setActive(index);
    show(
      event,
      <>
        <span className={hoverLabel}>
          <span
            className={`size-[11px] shrink-0 rounded-full ${STATUS_DOTS[index]}`}
            aria-hidden="true"
          />
          {label}
        </span>
        <strong className={hoverValue}>
          {value.toLocaleString("en")}
          {Number.isFinite(total) && total > 0
            ? ` of ${total.toLocaleString("en")} documents`
            : value === 1
              ? " document"
              : " documents"}
        </strong>
        <span className="mt-0.5 block text-[12.5px] text-spot-muted">
          {detail}
        </span>
      </>,
    );
  };
  const leaveRing = () => {
    setActive(null);
    hide();
  };

  return (
    <section className={statusPanel} aria-labelledby="document-status-title">
      <div className={statusCard} ref={containerRef}>
        <h2
          id="document-status-title"
          className={`${sectionTitle} max-[1359px]:col-span-full`}
        >
          Document status
        </h2>
        <StatusRings
          paid={snapshot.paid}
          partial={snapshot.partial}
          unpaid={snapshot.unpaid}
          total={total}
          active={active}
          onHover={hoverRing}
          onLeave={leaveRing}
        />
        <dl className={statusLegend}>
          {statuses.map(([label, detail, value], index) => (
            <div
              className={`${statusRow(index)} ${active != null && active !== index ? "opacity-[.45]" : ""}`}
              key={label}
              onMouseEnter={() => setActive(index)}
              onMouseLeave={() => setActive(null)}
            >
              <dt className="flex min-w-0 items-start gap-3 text-[16px]">
                <span
                  className={`mt-1 size-[11px] shrink-0 rounded-full ${STATUS_DOTS[index]}`}
                  aria-hidden="true"
                />
                <span>
                  <span>{label}</span>
                  <small className="mt-0.5 block text-[13px] text-spot-muted">
                    {detail}
                  </small>
                </span>
              </dt>
              <dd className="shrink-0 text-[40px] max-[700px]:text-[36px]">
                <DashboardNumber value={value.toLocaleString("en")} />
              </dd>
            </div>
          ))}
        </dl>
        <p className={`${panelNote} max-[1359px]:col-span-full`}>
          Each ring is a share of all documents
        </p>
        <HoverCard card={card} />
      </div>
      <div className={overdueBar}>
        <span
          className="grid size-11 shrink-0 place-items-center rounded-[15px] bg-white/75 text-overdue-icon compact:size-[38px] compact:rounded-[13px] max-[700px]:size-9 max-[700px]:rounded-xl [&>svg]:size-[19px] [&>svg]:stroke-[1.6]"
          aria-hidden="true"
        >
          <FiAlertCircle />
        </span>
        <dl className="flex min-w-0 flex-1 items-center justify-between gap-4 text-overdue-ink max-[700px]:gap-2.5">
          <dt className="text-[14.5px]">
            Overdue
            <span className="mt-0.5 block text-[12.5px] text-overdue-muted max-[700px]:text-[12px]">
              Documents past their due date
            </span>
          </dt>
          <dd className="text-[44px] compact:text-[38px] max-[700px]:text-[38px]">
            <DashboardNumber value={snapshot.overdue.toLocaleString("en")} />
          </dd>
        </dl>
      </div>
    </section>
  );
};

// The status column grows from 360px in a 1360px-wide window to 540px at
// 1680px, so the finance cards never get narrower than they are at 1360px.
export const overviewLayout =
  "grid min-h-[724px] min-w-0 flex-1 grid-cols-[minmax(0,1fr)_clamp(360px,calc(56.25vw_-_405px),540px)] gap-6 compact:min-h-[635px] compact:gap-5 max-[1359px]:min-h-0 max-[1359px]:grid-cols-[minmax(0,1fr)] max-[700px]:gap-5";
// Extra height is shared in the design's 380 : 394 ratio. On short windows
// the payment history comes first, up to 385px, and the financial summary
// takes the rest. The minimums are the smallest the panels may get before
// the page scrolls.
export const mainColumn =
  "grid min-h-0 min-w-0 grid-rows-[minmax(340px,380fr)_minmax(362px,394fr)] gap-[22px] compact:grid-rows-[1fr_minmax(344px,385px)] compact:gap-4 max-[1359px]:grid-rows-[340px_394px] max-[700px]:flex max-[700px]:flex-col max-[700px]:gap-5";
export const historyPanel =
  "relative flex min-h-0 min-w-0 animate-ov-rise flex-col gap-1.5 rounded-[30px] border border-white/90 bg-white/60 px-7 pb-[18px] pt-[22px] backdrop-blur-[16px] [--rise-delay:160ms] compact:px-[22px] compact:pb-3.5 compact:pt-4 max-[1359px]:px-5 max-[700px]:gap-3 max-[700px]:rounded-3xl max-[700px]:p-[18px]";
export const historyHeading =
  "flex min-w-0 flex-[0_0_40px] items-center justify-between gap-4 compact:basis-8 max-[700px]:basis-auto max-[700px]:flex-wrap max-[700px]:gap-x-3 max-[700px]:gap-y-0.5";

export const DashboardLoading = () => (
  <div
    className={overviewLayout}
    role="status"
    aria-label="Loading your business overview"
  >
    <span className="sr-only">Loading your business overview…</span>
    <div className={mainColumn} aria-hidden="true">
      <div className={financePanel}>
        <div className={financePanelHeading}>
          <Skeleton className="h-6 w-[185px] rounded-md" />
        </div>
        <div className={financeCards}>
          {["blue", "lilac", "peach"].map((tone) => (
            <div className={financeCard(tone)} key={tone}>
              <Skeleton className="h-[37px] w-[150px] rounded-full" />
              <div>
                <Skeleton className="h-14 w-[82%] rounded-md" />
                <Skeleton className="mt-3 h-[13px] w-[70%] rounded-md" />
              </div>
              <Skeleton className="h-2.5 w-full rounded-md" />
            </div>
          ))}
        </div>
      </div>
      <div className={historyPanel}>
        <div className={historyHeading}>
          <Skeleton className="h-6 w-[185px] rounded-md" />
        </div>
        <div className="grid min-h-[248px] flex-1 grid-rows-[repeat(5,minmax(44px,1fr))]">
          {Array.from({ length: 5 }, (_, index) => (
            <div
              className="flex items-center gap-3 border-t border-[#23273512]"
              key={index}
            >
              <Skeleton className="size-8 rounded-[11px]" />
              <Skeleton className="h-3 w-1/4 rounded-md" />
              <Skeleton className="ml-auto h-3 w-[15%] rounded-md" />
            </div>
          ))}
        </div>
        <Skeleton className="mt-2.5 h-[22px] w-[116px] rounded-md" />
      </div>
    </div>
    <div className={statusPanel} aria-hidden="true">
      <div className={statusCard}>
        <Skeleton className="h-6 w-[185px] rounded-md max-[1359px]:col-span-full" />
        <StatusRings paid={0} partial={0} unpaid={0} total={0} />
        <div className={statusLegend}>
          {Array.from({ length: 3 }, (_, index) => (
            <div className={statusRow(index)} key={index}>
              <Skeleton className="h-[22px] w-[116px] rounded-md" />
              <Skeleton className="h-[46px] w-12 rounded-md" />
            </div>
          ))}
        </div>
        <Skeleton className="h-[13px] w-[70%] rounded-md max-[1359px]:col-span-full" />
      </div>
      <div className={overdueBar}>
        <Skeleton className="size-8 rounded-[11px]" />
        <Skeleton className="h-[13px] w-[70%] rounded-md" />
      </div>
    </div>
  </div>
);
