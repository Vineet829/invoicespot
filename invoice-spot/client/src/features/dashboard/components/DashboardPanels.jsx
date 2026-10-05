import { useRef, useState } from "react";
import { skeleton, surface } from "../../../components/workspace/pastel";

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

// The overview's charts and lists sit on the frosted panel surface, with
// small links to the full lists beside their titles.
export const overviewPanel = `${surface} relative flex min-w-0 animate-ov-rise flex-col gap-2.5 px-6 pb-4 pt-5 max-[700px]:rounded-3xl max-[700px]:p-[18px]`;
export const panelLink =
  "inline-flex shrink-0 items-center gap-1 whitespace-nowrap text-[12.5px] text-spot-night underline-offset-4 transition-colors hover:text-spot-violet hover:underline coarse:min-h-11 [&>svg]:size-[13px] [&>svg]:stroke-[1.5]";
// A panel's way to start what it has none of yet, such as recording an
// expense: a white key, greyed out when the role can't use it.
export const panelAction =
  "inline-flex h-9 items-center gap-1.5 whitespace-nowrap rounded-[11px] bg-white px-3.5 text-[13px] text-spot-night transition-colors hover:text-spot-violet disabled:cursor-default disabled:bg-white/40 disabled:text-spot-disabled disabled:opacity-100 coarse:min-h-11 [&>svg]:size-3.5 [&>svg]:stroke-[1.8]";

// The overview's rows below the tiles: the cash flow across the width,
// what needs attention beside the top clients from 1200px, then the lists
// that sum up the other tabs, two side by side from 1200px, with one left
// over across both, and all side by side from 1440px, each as wide as the
// others. Narrower, the lists would shorten most names.
export const attentionPair =
  "grid min-w-0 gap-5 min-[1200px]:grid-cols-[minmax(0,1fr)_480px]";
export const listRow =
  "grid min-w-0 gap-5 min-[1200px]:grid-cols-2 min-[1200px]:[&>:nth-child(odd):last-child]:col-span-2 min-[1440px]:grid-cols-none min-[1440px]:auto-cols-fr min-[1440px]:grid-flow-col min-[1440px]:[&>:nth-child(odd):last-child]:col-span-1";

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
    <div aria-hidden="true">
      <div className={overviewPanel}>
        <Skeleton className="h-6 w-[120px] rounded-md" />
        <Skeleton className="h-[26px] w-[45%] rounded-md" />
        <Skeleton className="h-[294px] w-full rounded-2xl max-[700px]:h-[222px]" />
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
  </div>
);
