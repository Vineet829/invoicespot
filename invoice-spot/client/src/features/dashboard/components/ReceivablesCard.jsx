import { Link } from "react-router-dom";
import { FiChevronRight } from "react-icons/fi";
import { money } from "../../../components/workspace/Workspace";
import { countOf } from "../../../utils/counts";
import {
  AGE_BANDS,
  formatMoney,
  invoiceCount,
  owedBands,
  receivablesLink,
} from "../dashboardModel";
import {
  DashboardNumber,
  financeFigure,
  financeLabel,
  financeTones,
  receivablesCard,
} from "./DashboardPanels";

const colors = financeTones.peach;

// The bar's parts: what is not due yet in the not-due lavender, then each
// overdue band in rust, darker the later it is. An older server's answer
// has no bands, so what is overdue is one part. A part with nothing in it
// is left out; the rest keep a sliver at least.
const barParts = (owedNow, bands) =>
  [
    { key: "current", amount: owedNow.notDue, color: "bg-owed-due" },
    ...(bands
      ? AGE_BANDS.map((band) => ({
          key: band.key,
          amount: bands[band.key]?.amount,
          color: band.color,
        }))
      : [
          { key: "overdue", amount: owedNow.overdue, color: "bg-owed-overdue" },
        ]),
  ].filter((part) => Number(part.amount) > 0);

// An overdue band's amount in the card's ink, its cents smaller and lighter.
const BandAmount = ({ value, currency }) => {
  const text = formatMoney(value, currency);
  return (
    <span
      className={`font-display text-[18px] font-light tracking-[-0.03em] ${colors.ink}`}
    >
      {text.slice(0, -3)}
      <span className={`text-[0.7em] ${colors.decimal}`}>{text.slice(-3)}</span>
    </span>
  );
};

// Current or Overdue under the bar: a link to that part of what is owed on
// the Receivables view, with its key, its figure as large as half the card
// allows, up to 34px, and how many invoices it is on.
const Split = ({ to, label, swatch, value, invoices, currency, ariaLabel }) => {
  const text = formatMoney(value, currency);
  return (
    <Link
      to={to}
      aria-label={ariaLabel}
      className="-mx-3 -my-2 flex min-w-0 flex-col gap-2 rounded-2xl px-3 py-2 transition-colors hover:bg-white/45"
      style={{ "--chars": text.length }}
    >
      <span className={`flex items-center gap-2 text-[14px] ${colors.label}`}>
        <span
          className={`size-2.5 shrink-0 rounded-full ${swatch}`}
          aria-hidden="true"
        />
        {label}
        <FiChevronRight
          aria-hidden="true"
          className={`size-[15px] stroke-[1.5] ${colors.muted}`}
        />
      </span>
      <span className={colors.ink}>
        <DashboardNumber
          value={text}
          className="[font-size:min(34px,calc((50cqi_-_16px)_/_(var(--chars)_*_0.5)))]"
          decimalClass={colors.decimal}
        />
      </span>
      <span className={`text-[13px] ${colors.muted}`}>
        {invoiceCount(invoices)}
      </span>
    </Link>
  );
};

// What clients owe today, as Zoho Invoice's total receivables shows it, in
// a card of its own beside what needs attention: the total, a bar split
// into what is not due yet and what is overdue by how late it is, then
// Current and Overdue, and the overdue bands. It is as of today, whatever
// the period shown. Current opens what is coming in on the Documents tab's
// Receivables view, and Overdue what is overdue there, in the same
// currency when one was chosen.
const ReceivablesCard = ({ snapshot, currencyChosen }) => {
  const { currency, owedNow } = snapshot;
  const chosen = currencyChosen ? currency : undefined;
  const bands = owedBands(owedNow);
  const parts = barParts(owedNow, bands);
  const current = owedNow.invoices - owedNow.overdueInvoices;
  const total = formatMoney(owedNow.total, currency);
  return (
    <section className={receivablesCard} aria-labelledby="receivables-title">
      {/* The name keeps to one line. */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h2
          id="receivables-title"
          className={`${financeLabel("peach")} whitespace-nowrap`}
        >
          Total receivables
        </h2>
        <span className={`text-[13px] ${colors.muted}`}>as of today</span>
      </div>
      <div
        className="flex flex-wrap items-baseline gap-x-4 gap-y-1"
        style={{ "--chars": total.length }}
      >
        <span className={colors.ink}>
          <DashboardNumber
            value={total}
            className={financeFigure}
            decimalClass={colors.decimal}
          />
        </span>
        <span className={`text-[16px] ${colors.muted}`}>
          {owedNow.invoices
            ? `across ${countOf(owedNow.invoices, "unpaid invoice", "unpaid invoices")}`
            : "No unpaid invoices"}
        </span>
      </div>
      {/* The same figures are words below, so the bar is left to the eye. */}
      <div
        className={`flex h-4 gap-0.5 overflow-hidden rounded-full ${parts.length ? "" : "bg-white/50"}`}
        aria-hidden="true"
      >
        {parts.map((part) => (
          <span
            className={`min-w-1 ${part.color}`}
            key={part.key}
            style={{ flex: `${Number(part.amount)} 1 0` }}
          />
        ))}
      </div>
      {/* Current and Overdue side by side, then the overdue bands, two
          across in the card's column and four across when it has the
          whole width. */}
      <div className="flex flex-col gap-5">
        <div className="grid grid-cols-2 gap-4">
          <Split
            to={receivablesLink(undefined, chosen)}
            label="Current"
            swatch="bg-owed-due"
            value={owedNow.notDue}
            invoices={current}
            currency={currency}
            ariaLabel={`Current, not due yet: ${money(owedNow.notDue, currency)} on ${invoiceCount(current)}. View what is coming in`}
          />
          <Split
            to={receivablesLink("overdue", chosen)}
            label="Overdue"
            swatch="bg-owed-overdue"
            value={owedNow.overdue}
            invoices={owedNow.overdueInvoices}
            currency={currency}
            ariaLabel={`Overdue: ${money(owedNow.overdue, currency)} on ${invoiceCount(owedNow.overdueInvoices)}. View the overdue invoices`}
          />
        </div>
        {bands && (
          <ul
            className="m-0 grid list-none grid-cols-2 gap-x-6 gap-y-3.5 p-0 [@container_finance-card_(min-width:760px)]:grid-cols-4"
            aria-label="Overdue by days late"
          >
            {AGE_BANDS.map((band) => {
              const { amount = 0, invoices = 0 } = bands[band.key] || {};
              return (
                <li
                  className="flex min-w-0 flex-col justify-between gap-1.5"
                  key={band.key}
                >
                  <span
                    className={`flex items-center gap-2 whitespace-nowrap text-[13px] ${colors.muted}`}
                  >
                    <span
                      className={`size-2.5 shrink-0 rounded-[3px] ${band.color}`}
                      aria-hidden="true"
                    />
                    {band.label}
                  </span>
                  <span
                    className={`flex items-baseline gap-1.5 whitespace-nowrap text-[13px] ${colors.muted}`}
                    aria-hidden="true"
                  >
                    <BandAmount value={amount} currency={currency} />·{" "}
                    {Number(invoices).toLocaleString("en")}
                  </span>
                  <span className="sr-only">
                    {" "}
                    late: {money(amount, currency)} on {invoiceCount(invoices)}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
};

export default ReceivablesCard;
