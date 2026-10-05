import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FiAlertCircle } from "react-icons/fi";
import { getStatusRingRatio, statusLink } from "../dashboardModel";
import {
  hoverLabel,
  hoverValue,
  panelNote,
  sectionTitle,
  skeleton,
  surface,
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

// The financial cards' pastel tones, as the Reports page shows them.
const CARD_TONES = {
  blue: {
    card: "bg-card-blue",
    label: "text-card-blue-label",
    icon: "text-card-blue-icon",
    ink: "text-card-blue-ink",
    muted: "text-card-blue-muted",
    decimal: "text-card-blue-decimal",
  },
  lilac: {
    card: "bg-card-lilac",
    label: "text-card-lilac-label",
    icon: "text-card-lilac-icon",
    ink: "text-card-lilac-ink",
    muted: "text-card-lilac-muted",
    decimal: "text-card-lilac-decimal",
  },
  peach: {
    card: "bg-card-peach",
    label: "text-card-peach-label",
    icon: "text-card-peach-icon",
    ink: "text-card-peach-ink",
    muted: "text-card-peach-muted",
    decimal: "text-card-peach-decimal",
  },
};

export const financeCards =
  "grid min-h-0 grid-cols-3 gap-4 max-[1359px]:gap-3 max-[700px]:grid-cols-1";
const financeCard = (tone) =>
  `flex min-w-0 flex-col justify-between gap-[18px] rounded-[22px] p-6 [container-name:finance-card] [container-type:inline-size] compact:gap-2.5 compact:px-3.5 compact:py-[18px] max-[1359px]:p-[18px] max-[700px]:min-h-[240px] max-[700px]:p-[22px] ${CARD_TONES[tone].card}`;

export const FinancialCard = ({ label, value, detail, icon: Icon, tone }) => {
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
    className="mx-auto block h-auto w-[260px] max-w-full shrink-0 overflow-visible min-[1200px]:w-[170px]"
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

// The rings beside their legend; on phones, the legend under the rings.
// From 1200px wide the rings are smaller and the spacing tighter, so the
// panel stays short, whether it has a row to itself or, from 1440px, sits
// beside the latest payments, whose height then goes around the rings.
export const statusPanel =
  "flex min-w-0 animate-ov-rise flex-col gap-5 rounded-[30px] border border-white/85 bg-white/50 p-[26px] shadow-panel-soft backdrop-blur-[16px] [--rise-delay:400ms] min-[1200px]:gap-3.5 min-[1200px]:p-[18px] max-[700px]:gap-4 max-[700px]:rounded-3xl max-[700px]:p-[18px]";
export const statusCard =
  "relative grid min-w-0 flex-1 grid-cols-[260px_minmax(0,1fr)] grid-rows-[auto_1fr_auto] items-center gap-x-8 gap-y-5 rounded-3xl border border-white/95 bg-white/[.72] px-[26px] py-6 min-[1200px]:grid-cols-[170px_minmax(0,1fr)] min-[1200px]:gap-x-6 min-[1200px]:gap-y-3 min-[1200px]:px-5 min-[1200px]:py-[18px] max-[700px]:flex max-[700px]:flex-col max-[700px]:gap-7 max-[700px]:rounded-[20px] max-[700px]:px-5 max-[700px]:py-[22px] max-[700px]:[&>:not(svg)]:w-full";
export const statusLegend = "col-start-2 row-start-2";
const statusRow = (index) =>
  `relative flex items-center justify-between gap-4 py-3 transition-opacity duration-200 min-[1200px]:py-2 ${index > 0 ? "border-t border-[#23273514]" : ""}`;
// A status with documents opens them: its name is a link that covers the
// whole row, above the rolling digits, and the row shows the link's focus.
const statusLinkClass =
  "after:absolute after:inset-0 after:z-[1] after:content-[''] hover:underline hover:underline-offset-4 focus-visible:outline-none";
const statusFocus =
  "has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[#5b47a0]";
const overdueBar =
  "flex min-h-[82px] items-center gap-4 rounded-[22px] border border-white/90 bg-overdue/85 px-5 py-[18px] min-[1200px]:min-h-0 min-[1200px]:px-4 min-[1200px]:py-3 max-[700px]:gap-3 max-[700px]:p-4";

// The overdue box opens the overdue invoices, when there are any.
const OverdueBox = ({ count, children }) =>
  count > 0 ? (
    <Link
      to={statusLink("overdue")}
      className={`${overdueBar} transition-colors hover:bg-overdue`}
      aria-label={`Overdue: ${count.toLocaleString("en")} unpaid ${count === 1 ? "invoice" : "invoices"} past their due date. Open them`}
    >
      {children}
    </Link>
  ) : (
    <div className={overdueBar}>{children}</div>
  );

// The documents with each payment status, and the overdue invoices, open the
// Documents list with that filter. The counts take in every currency.
export const DocumentStatus = ({ snapshot, total }) => {
  const [active, setActive] = useState(null);
  const { containerRef, card, show, hide } = useHoverCard();
  const statuses = [
    ["Paid", "Settled in full", snapshot.paid, "paid"],
    ["Partially paid", "Part received", snapshot.partial, "partial"],
    ["Unpaid", "Nothing received yet", snapshot.unpaid, "unpaid"],
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
        <div className="col-span-full flex items-baseline justify-between gap-3">
          <h2 id="document-status-title" className={sectionTitle}>
            Document status
          </h2>
          {Number.isFinite(total) && (
            <p className={panelNote}>
              {total.toLocaleString("en")}{" "}
              {total === 1 ? "document" : "documents"}
            </p>
          )}
        </div>
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
          {statuses.map(([label, detail, value, status], index) => (
            <div
              className={`${statusRow(index)} ${value > 0 ? statusFocus : ""} ${active != null && active !== index ? "opacity-[.45]" : ""}`}
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
                  {value > 0 ? (
                    <Link
                      to={statusLink(status)}
                      className={statusLinkClass}
                      aria-label={`${label}: ${value.toLocaleString("en")} ${value === 1 ? "document" : "documents"}. Open them`}
                    >
                      {label}
                    </Link>
                  ) : (
                    <span>{label}</span>
                  )}
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
        <p className={`${panelNote} col-span-full`}>
          Each ring is a share of all documents
        </p>
        <HoverCard card={card} />
      </div>
      <OverdueBox count={snapshot.overdue}>
        <span
          className="grid size-11 shrink-0 place-items-center rounded-[15px] bg-white/75 text-overdue-icon min-[1200px]:size-[38px] min-[1200px]:rounded-[13px] max-[700px]:size-9 max-[700px]:rounded-xl [&>svg]:size-[19px] [&>svg]:stroke-[1.6]"
          aria-hidden="true"
        >
          <FiAlertCircle />
        </span>
        <dl className="flex min-w-0 flex-1 items-center justify-between gap-4 text-overdue-ink max-[700px]:gap-2.5">
          <dt className="text-[14.5px]">
            Overdue
            <span className="mt-0.5 block text-[12.5px] text-overdue-muted max-[700px]:text-[12px]">
              Unpaid invoices past their due date
            </span>
          </dt>
          <dd className="text-[44px] min-[1200px]:text-[38px] max-[700px]:text-[38px]">
            <DashboardNumber value={snapshot.overdue.toLocaleString("en")} />
          </dd>
        </dl>
      </OverdueBox>
    </section>
  );
};

export const historyPanel =
  "relative flex min-w-0 animate-ov-rise flex-col gap-1.5 rounded-[30px] border border-white/90 bg-white/60 px-6 pb-[18px] pt-[22px] backdrop-blur-[16px] [--rise-delay:400ms] max-[700px]:gap-3 max-[700px]:rounded-3xl max-[700px]:p-[18px]";
export const historyHeading =
  "flex min-w-0 flex-[0_0_40px] items-center justify-between gap-4 max-[700px]:basis-auto max-[700px]:flex-wrap max-[700px]:gap-x-3 max-[700px]:gap-y-0.5";

// The overview's charts and lists sit on the frosted panel surface, with
// small links to the full lists beside their titles.
export const overviewPanel = `${surface} relative flex min-w-0 animate-ov-rise flex-col gap-2.5 px-6 pb-4 pt-5 max-[700px]:rounded-3xl max-[700px]:p-[18px]`;
export const panelLink =
  "inline-flex shrink-0 items-center gap-1 whitespace-nowrap text-[12.5px] text-spot-night underline-offset-4 transition-colors hover:text-spot-violet hover:underline coarse:min-h-11 [&>svg]:size-[13px] [&>svg]:stroke-[1.5]";
// A panel's way to start what it has none of yet, such as recording an
// expense: a white key, greyed out when the role can't use it.
export const panelAction =
  "inline-flex h-9 items-center gap-1.5 whitespace-nowrap rounded-[11px] bg-white px-3.5 text-[13px] text-spot-night transition-colors hover:text-spot-violet disabled:cursor-default disabled:bg-white/40 disabled:text-spot-disabled disabled:opacity-100 coarse:min-h-11 [&>svg]:size-3.5 [&>svg]:stroke-[1.8]";

// The overview's rows below the tiles: the cash flow beside what is owed
// from 1360px wide, what needs attention beside the top clients from
// 1200px, then the lists that sum up the other tabs, two side by side from
// 1200px, with one left over across both, and all side by side from 1440px,
// each as wide as the others; and the latest payments beside the document
// status from 1440px. Narrower, the payments' columns would break names and
// headings inside words, and the lists would shorten most names. The top
// clients and the document status are as wide as each other.
export const chartPair =
  "grid min-w-0 gap-5 min-[1360px]:grid-cols-[minmax(0,1fr)_clamp(400px,36%,560px)]";
export const attentionPair =
  "grid min-w-0 gap-5 min-[1200px]:grid-cols-[minmax(0,1fr)_480px]";
export const listRow =
  "grid min-w-0 gap-5 min-[1200px]:grid-cols-2 min-[1200px]:[&>:nth-child(odd):last-child]:col-span-2 min-[1440px]:grid-cols-none min-[1440px]:auto-cols-fr min-[1440px]:grid-flow-col min-[1440px]:[&>:nth-child(odd):last-child]:col-span-1";
export const historyPair =
  "grid min-w-0 gap-5 min-[1440px]:grid-cols-[minmax(0,1fr)_480px]";

// The overview's tiles: four across a wide window, two below 1200px and one
// on phones.
export const tileRow =
  "grid min-w-0 animate-ov-rise grid-cols-4 gap-[18px] [--rise-delay:80ms] max-[1199px]:grid-cols-2 max-[700px]:gap-4 max-[599px]:grid-cols-1";
export const tileCard =
  "flex min-w-0 flex-col gap-2.5 rounded-[22px] px-5 pb-4 pt-[18px]";

// A list with a bar in each row, such as Spending, while it loads: its
// title, the figure or words under it, then five rows.
const BarListLoading = ({ lead, className = "" }) => (
  <div className={`${overviewPanel} ${className}`}>
    <Skeleton className="h-6 w-[120px] rounded-md" />
    {lead}
    {Array.from({ length: 5 }, (_, index) => (
      <div className="flex items-center gap-2.5 py-[7px]" key={index}>
        <span className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-3 w-2/5 rounded-md" />
          <Skeleton className="h-1.5 w-full rounded-full" />
        </span>
        <Skeleton className="h-3 w-[72px] rounded-md" />
      </div>
    ))}
  </div>
);

export const DashboardLoading = () => (
  <div
    className="flex min-w-0 flex-col gap-5"
    role="status"
    aria-label="Loading your business overview"
  >
    <span className="sr-only">Loading your business overview…</span>
    <div className={tileRow} aria-hidden="true">
      {["bg-card-blue", "bg-card-lilac", "bg-card-sage", "bg-card-peach"].map(
        (tone) => (
          <div className={`${tileCard} ${tone}`} key={tone}>
            <Skeleton className="h-[27px] w-[92px] rounded-full" />
            <Skeleton className="h-10 w-[70%] rounded-md" />
            <Skeleton className="h-[22px] w-1/2 rounded-md" />
            <Skeleton className="mt-auto h-10 w-full rounded-md" />
          </div>
        ),
      )}
    </div>
    <div className={chartPair} aria-hidden="true">
      <div className={overviewPanel}>
        <Skeleton className="h-6 w-[120px] rounded-md" />
        <Skeleton className="h-[26px] w-[45%] rounded-md" />
        <Skeleton className="h-[294px] w-full rounded-2xl max-[700px]:h-[222px]" />
      </div>
      <div className={overviewPanel}>
        <Skeleton className="h-6 w-[200px] rounded-md" />
        <Skeleton className="h-9 w-[55%] rounded-md" />
        <Skeleton className="mt-auto h-[196px] w-full rounded-2xl max-[700px]:h-40" />
      </div>
    </div>
    <div className={attentionPair} aria-hidden="true">
      <div className={overviewPanel}>
        <Skeleton className="h-6 w-[200px] rounded-md" />
        {Array.from({ length: 4 }, (_, index) => (
          <div className="flex items-center gap-3 py-[9px]" key={index}>
            <Skeleton className="size-[34px] rounded-xl" />
            <Skeleton className="h-3 w-1/4 rounded-md" />
            <Skeleton className="ml-auto h-3 w-[15%] rounded-md" />
          </div>
        ))}
      </div>
      <BarListLoading
        lead={<Skeleton className="h-[13px] w-[55%] rounded-md" />}
        className="[--rise-delay:240ms]"
      />
    </div>
    <div className={listRow} aria-hidden="true">
      <BarListLoading
        lead={<Skeleton className="h-[30px] w-[45%] rounded-md" />}
        className="[--rise-delay:320ms]"
      />
      <BarListLoading
        lead={<Skeleton className="h-[13px] w-[55%] rounded-md" />}
        className="[--rise-delay:320ms]"
      />
      <div className={`${overviewPanel} [--rise-delay:320ms]`}>
        <Skeleton className="h-6 w-[120px] rounded-md" />
        {[3, 2].map((rows, part) => (
          <div
            className={`flex flex-col gap-2 ${part ? "border-t border-[#23273512] pt-2.5" : ""}`}
            key={part}
          >
            <span className="flex items-center gap-2">
              <Skeleton className="size-7 rounded-[10px]" />
              <Skeleton className="h-3.5 w-2/5 rounded-md" />
            </span>
            {Array.from({ length: rows }, (_, index) => (
              <Skeleton className="h-11 w-full rounded-[14px]" key={index} />
            ))}
          </div>
        ))}
      </div>
    </div>
    <div className={historyPair} aria-hidden="true">
      <div className={historyPanel}>
        <div className={historyHeading}>
          <Skeleton className="h-6 w-[185px] rounded-md" />
        </div>
        <div className="grid min-h-[284px] flex-1 grid-rows-[repeat(5,minmax(44px,1fr))]">
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
      <div className={statusPanel}>
        <div className={statusCard}>
          <Skeleton className="col-span-full h-6 w-[185px] rounded-md" />
          <StatusRings paid={0} partial={0} unpaid={0} total={0} />
          <div className={statusLegend}>
            {Array.from({ length: 3 }, (_, index) => (
              <div className={statusRow(index)} key={index}>
                <Skeleton className="h-[22px] w-[116px] rounded-md" />
                <Skeleton className="h-[46px] w-12 rounded-md" />
              </div>
            ))}
          </div>
          <Skeleton className="col-span-full h-[13px] w-[70%] rounded-md" />
        </div>
        <div className={overdueBar}>
          <Skeleton className="size-8 rounded-[11px]" />
          <Skeleton className="h-[13px] w-[70%] rounded-md" />
        </div>
      </div>
    </div>
  </div>
);
