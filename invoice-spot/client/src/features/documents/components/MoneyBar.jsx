import { Link } from "react-router-dom";
import { FiAlertCircle, FiChevronRight } from "react-icons/fi";
import { Amount, money } from "../../../components/workspace/Workspace";
import {
  panelNote,
  skeleton,
  textLink,
} from "../../../components/workspace/pastel";
import { countOf, invoiceCount } from "../../../utils/counts";
import { formatAmount } from "../../../utils/money";
import { outstandingLink, overdueLink, paymentsLink } from "../listLinks";

// The bar's cards sit on the panel surface, with a ring on the part whose
// list is the one shown.
const card =
  "min-w-0 rounded-[22px] border border-white/90 bg-white/[.66] px-[22px] py-4 shadow-panel backdrop-blur-[16px] max-[700px]:rounded-[20px] max-[700px]:px-[18px]";
const ring = "ring-2 ring-spot-night ring-offset-2 ring-offset-white";
const bar =
  "grid min-w-0 gap-[18px] min-[1100px]:grid-cols-[minmax(0,1fr)_minmax(240px,380px)] max-[700px]:gap-4";
const legendItem = "inline-flex items-center gap-[7px] whitespace-nowrap";
const legendLink = `${legendItem} -mx-2 rounded-full px-2 underline-offset-4 transition-colors hover:text-spot-violet hover:underline coarse:min-h-11 [&>svg]:size-3 [&>svg]:stroke-[1.5]`;
const HINT = "The bar opens every invoice owed; Overdue opens the late ones";

const Swatch = ({ color }) => (
  <span
    className={`size-2.5 shrink-0 rounded-[3px] ${color}`}
    aria-hidden="true"
  />
);

// While the figures load, empty cards keep the bar's room, so the list
// below stays where it is when they arrive. The hint is there unseen, so
// the first card wraps as it will.
const Placeholder = () => (
  <div className={bar} aria-hidden="true">
    <div className={`${card} flex flex-col gap-2.5`}>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
        <span className={`${skeleton} h-[26px] w-[260px] rounded-md`} />
        <span className={`invisible ${panelNote}`}>{HINT}</span>
      </div>
      <span className={`${skeleton} h-3.5 rounded-full`} />
      <span
        className={`${skeleton} h-[18px] w-[440px] rounded-md max-[700px]:coarse:h-[64px]`}
      />
    </div>
    <div className={`${card} flex flex-col justify-center gap-1.5`}>
      <span className={`${skeleton} h-[18px] w-[180px] rounded-md`} />
      <span className={`${skeleton} h-[34px] w-[150px] rounded-md`} />
    </div>
  </div>
);

// What is outstanding now, split into what is overdue and what is not due
// yet, and what came in over the last 30 days, as the overview counts them,
// from the summary `query`. The figures are in the list's currency, or the
// default one; with several currencies the amounts name theirs, and the
// links keep it. The whole bar opens every invoice owed, soonest due first;
// Overdue opens the late ones, and the card the payments behind its figure.
// `shows` says whether the page shows exactly a link's list. A business with
// nothing issued has no bar.
const MoneyBar = ({ query, shows = () => false }) => {
  const summary = query.currentData;
  if (!summary) {
    return query.isError && !query.isFetching ? (
      <p
        className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl border border-white/90 bg-white/60 px-4 py-2.5 text-[13px] text-spot-body [&>svg]:size-4 [&>svg]:shrink-0 [&>svg]:text-overdue-icon"
        role="alert"
      >
        <FiAlertCircle aria-hidden="true" />
        We couldn’t load what is owed and what came in.
        <button
          type="button"
          className={`${textLink} bg-transparent p-0 text-[13px] coarse:min-h-11`}
          onClick={query.refetch}
        >
          Try again
        </button>
      </p>
    ) : (
      <Placeholder />
    );
  }
  if (!summary.currencies?.length) return null;
  const { currency, outstanding, receivedLast30Days: received } = summary;
  const several = summary.currencies.length > 1;
  const linked = several ? currency : undefined;
  const owedTo = outstandingLink(linked);
  const overdueTo = overdueLink(linked);
  const receivedTo = paymentsLink("30d", linked);
  const late = outstanding.overdueInvoices;
  const notDue = outstanding.invoices - late;
  const parts = [
    ["overdue", "bg-owed-overdue"],
    ["notDue", "bg-owed-due"],
  ].filter(([key]) => Number(outstanding[key]) > 0);
  return (
    <section aria-label="What is owed and what came in" className={bar}>
      <div className={`${card} flex flex-col gap-2.5`}>
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <p className="flex flex-wrap items-baseline gap-x-2.5 text-[13px] text-spot-body">
            Outstanding
            <Amount
              value={outstanding.total}
              currency={currency}
              hideCode={!several}
              className="text-[22px] font-light tracking-[-0.03em] text-spot-ink"
            />
            <span className="text-[12.5px] text-spot-muted">
              {outstanding.invoices
                ? `on ${invoiceCount(outstanding.invoices)}`
                : "No unpaid invoices"}
            </span>
          </p>
          {outstanding.invoices > 0 && <p className={panelNote}>{HINT}</p>}
        </div>
        {outstanding.invoices > 0 ? (
          <Link
            to={owedTo}
            aria-label={`Outstanding: ${money(outstanding.total, currency)} on ${invoiceCount(outstanding.invoices)}, ${late ? `${money(outstanding.overdue, currency)} of it overdue` : "none of it overdue"}. Open the invoices owed, soonest due first`}
            aria-current={shows(owedTo) ? "true" : undefined}
            className={`flex h-3.5 gap-[3px] rounded-full transition-opacity hover:opacity-85 ${shows(owedTo) ? ring : ""}`}
          >
            {parts.map(([key, color]) => (
              <span
                key={key}
                className={`min-w-1 first:rounded-l-full last:rounded-r-full ${color}`}
                style={{ flex: `${Number(outstanding[key])} 1 0` }}
              />
            ))}
          </Link>
        ) : (
          <span
            className="block h-3.5 rounded-full bg-[#2327350d]"
            aria-hidden="true"
          />
        )}
        {outstanding.invoices > 0 && (
          <div className="flex flex-wrap justify-between gap-x-4 gap-y-1 text-[12.5px] text-spot-text">
            {late > 0 ? (
              <Link
                to={overdueTo}
                aria-label={`Overdue: ${money(outstanding.overdue, currency)} on ${invoiceCount(late)}. Open the overdue invoices`}
                aria-current={shows(overdueTo) ? "true" : undefined}
                className={`${legendLink} ${shows(overdueTo) ? ring : ""}`}
              >
                <Swatch color="bg-owed-overdue" />
                Overdue
                <strong className="font-medium">
                  {formatAmount(outstanding.overdue, currency)}
                </strong>
                <span className="text-spot-muted">· {invoiceCount(late)}</span>
                <FiChevronRight aria-hidden="true" />
              </Link>
            ) : (
              <span className={legendItem}>
                <Swatch color="bg-owed-overdue" />
                Nothing overdue
              </span>
            )}
            <span className={legendItem}>
              <Swatch color="bg-owed-due" />
              Not due yet
              <strong className="font-medium">
                {formatAmount(outstanding.notDue, currency)}
              </strong>
              <span className="text-spot-muted">· {invoiceCount(notDue)}</span>
            </span>
          </div>
        )}
      </div>
      <Link
        to={receivedTo}
        aria-label={`Received in the last 30 days: ${money(received.total, currency)} from ${countOf(received.payments, "payment", "payments")}. Open these payments`}
        aria-current={shows(receivedTo) ? "true" : undefined}
        className={`${card} flex flex-col justify-center gap-1.5 transition-shadow hover:shadow-float ${shows(receivedTo) ? ring : ""}`}
      >
        <span className="text-[13px] text-spot-body">
          Received in the last 30 days
        </span>
        <span className="flex flex-wrap items-baseline justify-between gap-x-3">
          <Amount
            value={received.total}
            currency={currency}
            hideCode={!several}
            className="text-[26px] font-light tracking-[-0.03em] text-spot-ink"
          />
          <span className="inline-flex items-center gap-1 text-[12.5px] text-spot-night [&>svg]:size-[13px] [&>svg]:stroke-[1.5]">
            {countOf(received.payments, "payment", "payments")}
            <FiChevronRight aria-hidden="true" />
          </span>
        </span>
      </Link>
    </section>
  );
};

export default MoneyBar;
