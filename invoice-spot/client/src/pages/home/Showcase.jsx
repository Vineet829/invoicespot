import {
  FiBarChart2,
  FiCheckCircle,
  FiChevronRight,
  FiChevronUp,
  FiCreditCard,
  FiFileText,
  FiGrid,
  FiPackage,
  FiSettings,
  FiUser,
  FiUsers,
} from "react-icons/fi";
import {
  primaryButton,
  secondaryButton,
} from "../../components/workspace/pastel";
import {
  OwedSplit,
  Sparkline,
  TONES,
  TrendColumns,
} from "../../features/dashboard/components/OverviewTiles";
import QrPattern from "./QrPattern";
import { rise } from "./motion";

// The hero's picture of the product: the overview of a sample business in
// a browser window, drawn at a small scale with the overview's own tiles
// and chart, and over it a UPI payment, a reported payment and an issued
// tax invoice. It is decoration; the text beside it says what it shows.

// The workspace sidebar, as the signed-in shell lists it.
const NAVIGATION = [
  ["Overview", FiGrid],
  ["Documents", FiFileText],
  ["Clients", FiUsers],
  ["Items", FiPackage],
  ["Expenses", FiCreditCard],
  ["Reports", FiBarChart2],
  ["Profile", FiUser],
  ["Settings", FiSettings],
];

// Twelve months of the sample business, in thousands.
const BILLED = [4.1, 5.8, 5.2, 7.9, 6.3, 6.9, 8.4, 5.6, 7.3, 4.4, 5.1, 6.6];
const RECEIVED = [3.2, 4.1, 3.8, 5.9, 4.6, 5.1, 6.2, 4.2, 5.4, 3.1, 3.9, 4.8];
const PROFIT = [3.4, 4.9, 4.2, 6.6, -1.1, 5.7, 7.2, 4.4, 6.1, 3.3, 4.3, 5.6];
const SPENT = [1.2, 1.6, 1.3, 2.2, 1.8, 2.4, 1.5, 2.1, 1.4, 1.9, 2.3, 1.7];
const OWED = { notDue: 17959.76, overdue: 6326.03 };

// How a figure moved against the twelve months before.
const Change = ({ percent }) => (
  <>
    <span className="inline-flex items-center gap-px rounded-full bg-white/80 py-[2px] pl-1 pr-1.5 text-[9.5px] font-medium leading-[1.3] text-change-up">
      <FiChevronUp className="size-2.5 stroke-[2.2]" />
      {percent}
    </span>
    <span>vs previous 12 months</span>
  </>
);

const TILES = [
  {
    title: "Billed",
    value: "68,848.08",
    tone: "blue",
    line: <Change percent="12.4%" />,
    chart: <Sparkline values={BILLED} colors={TONES.blue} className="h-6" />,
  },
  {
    title: "Received",
    value: "44,562.29",
    tone: "lilac",
    line: <Change percent="8.2%" />,
    chart: <Sparkline values={RECEIVED} colors={TONES.lilac} className="h-6" />,
  },
  {
    title: "Profit",
    value: "58,279.33",
    tone: "sage",
    line: <Change percent="15.1%" />,
    chart: <TrendColumns values={PROFIT} className="h-6" />,
  },
  {
    title: "Outstanding now",
    value: "24,285.79",
    tone: "peach",
    line: "10 invoices · 3 overdue",
    chart: (
      <OwedSplit owed={OWED} currency="INR" className="gap-1 text-[9.5px]" />
    ),
  },
];

// An overview tile at the window's scale.
const Tile = ({ title, value, tone, line, chart }) => {
  const colors = TONES[tone];
  return (
    <div
      className={`flex min-w-0 flex-col gap-1.5 rounded-[14px] px-3 pb-2.5 pt-3 ${colors.tile}`}
    >
      <span className="flex items-center justify-between gap-1">
        <span
          className={`whitespace-nowrap rounded-full bg-white/70 px-2 py-[3px] text-[10px] leading-[1.25] ${colors.label}`}
        >
          {title}
        </span>
        <FiChevronRight className={`size-3 stroke-[1.5] ${colors.muted}`} />
      </span>
      <span
        className={`block whitespace-nowrap font-display text-[22px] font-extralight leading-none tracking-[-0.05em] ${colors.ink}`}
      >
        {value.slice(0, -3)}
        <span className={`text-[0.56em] tracking-[-0.02em] ${colors.decimal}`}>
          {value.slice(-3)}
        </span>
      </span>
      <span
        className={`flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[9.5px] ${colors.muted}`}
      >
        {line}
      </span>
      <span className="mt-auto block pt-0.5">{chart}</span>
    </div>
  );
};

// Money in and out each month, as the overview's cash flow chart: money in
// stands on the zero line, money out hangs below it, and the dark line is
// what was left. The columns grow in after the window has risen.
const ZERO = 68;
const SCALE = Math.max(...BILLED) * 1.08;
const inHeight = (value) => (value / SCALE) * ZERO;
const outHeight = (value) => (value / SCALE) * ZERO;
const NET = BILLED.map(
  (value, index) =>
    `${((index + 0.5) / BILLED.length) * 100},${ZERO - inHeight(value) + outHeight(SPENT[index])}`,
);

const CashFlow = ({ motion }) => (
  <div className="flex flex-col gap-2 rounded-2xl bg-white/70 px-3.5 pb-3 pt-3">
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
      <span className="font-display text-[15px] font-light tracking-[-0.03em]">
        Cash flow
      </span>
      <ul className="m-0 flex list-none flex-wrap items-center gap-x-3 p-0 text-[10px] text-spot-body">
        <li className="flex items-center gap-1">
          <span className="size-2 rounded-[2px] bg-spot-violet" />
          Money in
        </li>
        <li className="flex items-center gap-1">
          <span className="size-2 rounded-[2px] bg-flow-out" />
          Money out
        </li>
        <li className="flex items-center gap-1">
          <span className="relative h-px w-3.5 bg-flow-net">
            <span className="absolute -top-[2px] left-1 size-[5px] rounded-full bg-flow-net" />
          </span>
          Net
        </li>
      </ul>
    </div>
    <div className="relative h-[150px] max-[700px]:h-[120px]">
      <span
        className="absolute inset-x-0 border-t border-[#2327354d]"
        style={{ top: `${ZERO}%` }}
      />
      <div className="absolute inset-0 grid grid-cols-12">
        {BILLED.map((value, index) => (
          <span className="relative" key={index}>
            <span
              className={`absolute inset-x-0 mx-auto w-[min(18px,56%)] origin-bottom rounded-t-[3px] bg-spot-violet ${motion ? "animate-ov-column" : ""}`}
              style={{
                bottom: `${100 - ZERO}%`,
                height: `${inHeight(value)}%`,
                "--column-delay": `${500 + index * 30}ms`,
              }}
            />
            <span
              className={`absolute inset-x-0 mx-auto w-[min(18px,56%)] origin-top rounded-b-[3px] bg-flow-out ${motion ? "animate-ov-column" : ""}`}
              style={{
                top: `${ZERO}%`,
                height: `${outHeight(SPENT[index])}%`,
                "--column-delay": `${500 + index * 30}ms`,
              }}
            />
          </span>
        ))}
      </div>
      <svg
        className="pointer-events-none absolute inset-0 size-full overflow-visible"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        focusable="false"
      >
        <polyline
          className="stroke-flow-net"
          points={NET.join(" ")}
          fill="none"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      {NET.map((point, index) => {
        const [x, y] = point.split(",");
        return (
          <span
            className="absolute -ml-1 -mt-1 size-2 rounded-full border-2 border-white bg-flow-net"
            key={index}
            style={{ left: `${x}%`, top: `${y}%` }}
          />
        );
      })}
    </div>
  </div>
);

const Overview = ({ motion }) => (
  <div className="grid grid-cols-[150px_minmax(0,1fr)] bg-spot-canvas bg-canvas max-[700px]:grid-cols-1">
    <div className="flex flex-col gap-[3px] border-r border-white/70 bg-white/[.35] px-2.5 py-4 max-[700px]:hidden">
      <span className="px-2 pb-3 font-display text-[15px] tracking-[-0.04em] text-spot-ink [&>span]:text-spot-brand">
        Invoice Spot<span>.</span>
      </span>
      {NAVIGATION.map(([label, Icon], index) => (
        <span
          className={`flex h-[30px] items-center gap-2 rounded-[10px] px-2.5 text-[11.5px] [&>svg]:size-[13px] [&>svg]:stroke-[1.5] ${index === 0 ? "bg-white font-medium text-spot-ink shadow-nav [&>svg]:text-spot-violet" : "text-spot-nav"}`}
          key={label}
        >
          <Icon />
          {label}
        </span>
      ))}
    </div>
    <div className="flex min-w-0 flex-col gap-3 px-5 pb-5 pt-4 max-[700px]:px-3.5 max-[700px]:pb-3.5">
      <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
        <span className="font-display text-[26px] font-extralight leading-none tracking-[-0.05em] text-spot-ink [&>span]:text-spot-brand">
          Business overview<span>.</span>
        </span>
        <span className="flex gap-1 text-[10px] text-spot-body">
          {["30 days", "90 days", "12 months", "This year"].map((period) => (
            <span
              className={`rounded-full px-2 py-[3px] ${period === "12 months" ? "bg-spot-night text-white" : "bg-white/60"}`}
              key={period}
            >
              {period}
            </span>
          ))}
        </span>
      </div>
      <div className="grid grid-cols-4 gap-2.5 max-[999px]:grid-cols-2">
        {TILES.map((tile) => (
          <Tile {...tile} key={tile.title} />
        ))}
      </div>
      <CashFlow motion={motion} />
    </div>
  </div>
);

const BrowserFrame = ({ children }) => (
  <div className="overflow-hidden rounded-[22px] border border-white/95 bg-white/[.55] shadow-[0_50px_90px_-50px_#30234f8c]">
    <div className="flex h-10 items-center gap-2 border-b border-[#2327350f] bg-white/70 px-4">
      <span className="size-[11px] rounded-full bg-[#f0b79d]" />
      <span className="size-[11px] rounded-full bg-card-lilac" />
      <span className="size-[11px] rounded-full bg-card-blue" />
      <span className="ml-3.5 flex h-6 min-w-0 max-w-[360px] flex-1 items-center rounded-lg bg-[#2327350d] px-3 text-[12px] text-spot-muted">
        <span className="truncate">invoice.confidental.online/dashboard</span>
      </span>
    </div>
    {children}
  </div>
);

// The cards over the window are the frosted surface, near enough opaque
// for the window not to show through their text.
const floating =
  "border border-white/90 bg-white/[.9] shadow-panel backdrop-blur-[16px]";

const PayCard = () => (
  <div
    className={`${floating} flex flex-col items-center gap-2.5 rounded-[28px] p-4 text-center`}
  >
    <QrPattern className="size-[150px]" />
    <p>
      <span className="block text-[12px] text-spot-muted">
        Pay INV-0042 by UPI
      </span>
      <span className="font-display text-[24px] font-light tracking-[-0.04em] text-spot-ink">
        ₹ 59,000<span className="text-[0.6em] text-spot-faint">.00</span>
      </span>
    </p>
    <span className="text-[12px] text-spot-muted">Scan with any UPI app</span>
  </div>
);

const ClaimCard = () => (
  <div className={`${floating} flex flex-col gap-3 rounded-[28px] p-4`}>
    <div className="flex items-center gap-2.5">
      <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-avatar-lilac text-[12px] text-avatar-lilac-ink">
        HP
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="text-[13.5px] font-medium text-spot-ink">
          Payment reported
        </span>
        <span className="text-[12px] text-spot-muted">
          Harbor &amp; Pine · UPI · just now
        </span>
      </span>
      <span className="ml-auto font-display text-[18px] font-light text-spot-ink">
        262.76
      </span>
    </div>
    <div className="flex gap-2 [&>span]:flex-1">
      <span className={primaryButton}>Confirm</span>
      <span className={secondaryButton}>Dismiss</span>
    </div>
  </div>
);

const IssuedToast = () => (
  <div
    className={`${floating} flex items-center gap-3 rounded-[18px] px-4 py-3`}
  >
    <span className="grid size-[34px] shrink-0 place-items-center rounded-[11px] bg-card-sage text-card-sage-icon [&>svg]:size-[17px] [&>svg]:stroke-[1.7]">
      <FiCheckCircle />
    </span>
    <span className="flex flex-col">
      <span className="text-[13.5px] font-medium text-spot-ink">
        Tax invoice INV-0042 issued
      </span>
      <span className="text-[12px] text-spot-muted">
        CGST 9% + SGST 9% · sent by email
      </span>
    </span>
  </div>
);

// From 960px the cards float over the window's edges: the payment at the
// bottom left, the reported payment on the bottom right corner with the
// issued invoice under it. Below 960px they line up under the window, the
// last one across the row.
const Showcase = ({ motion }) => (
  <>
    <p className="sr-only">
      A sample of the product: the overview of a business showing what it
      billed, received, earned and is owed, with its cash flow by month; a
      sample UPI code to pay an invoice; a payment a client reported, ready to
      confirm; and a tax invoice just issued and sent.
    </p>
    <div className="relative pb-[130px] max-[959px]:pb-0" aria-hidden="true">
      <div className={`mx-[6%] max-[959px]:mx-0 ${rise(motion, 120)}`}>
        <BrowserFrame>
          <Overview motion={motion} />
        </BrowserFrame>
      </div>
      <div className="max-[959px]:mt-4 max-[959px]:grid max-[959px]:grid-cols-[repeat(auto-fit,minmax(min(100%,250px),1fr))] max-[959px]:items-start max-[959px]:gap-4 max-[959px]:[&>:last-child]:col-span-full">
        <div
          className={`absolute bottom-1.5 left-0 w-[250px] max-[959px]:static max-[959px]:w-auto ${rise(motion, 240)}`}
        >
          <PayCard />
        </div>
        <div
          className={`absolute bottom-[70px] right-0 w-[320px] max-[959px]:static max-[959px]:w-auto ${rise(motion, 320)}`}
        >
          <ClaimCard />
        </div>
        <div
          className={`absolute bottom-0 right-0 max-[959px]:static ${rise(motion, 400)}`}
        >
          <IssuedToast />
        </div>
      </div>
    </div>
  </>
);

export default Showcase;
