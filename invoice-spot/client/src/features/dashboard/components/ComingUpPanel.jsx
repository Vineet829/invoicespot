import { Fragment } from "react";
import { Link } from "react-router-dom";
import { FiChevronRight, FiFileText, FiPlus, FiRepeat } from "react-icons/fi";
import { ActionLink, Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { countOf } from "../../../utils/counts";
import { useBusiness } from "../../business/permissions";
import {
  attentionDocuments,
  formatMoney,
  quotationRows,
  quotationsLink,
  recurringLink,
  runRows,
} from "../dashboardModel";
import { overviewPanel, panelAction, panelLink } from "./DashboardPanels";

// Each part's title, beside its icon on the tint the attention list gives
// the same kind, then the link to its list.
const partHeading = "flex items-center justify-between gap-3";
const partTitle = "flex min-w-0 items-center gap-2 text-[17px] font-medium";
const partIcon =
  "grid size-7 shrink-0 place-items-center rounded-[10px] [&>svg]:size-[15px] [&>svg]:stroke-[1.6]";
const card = "rounded-[14px] bg-white/55";
const amount = "text-[20px] font-light tracking-[-0.03em]";
// A detail's dot, kept on the line of the word after it.
const dot = "·\u00a0";
// A row that opens something, as tall on a touch screen as the overview's
// other small links, white under the pointer unless its list says otherwise.
const rowBase =
  "grid items-center gap-2.5 rounded-xl px-3 py-1.5 transition-colors coarse:min-h-11";
const rowLink = `${rowBase} hover:bg-white`;

// A quotation's row: its number, client and last valid day, each detail
// after the number starting with its dot, so a wrapped line never ends on
// one, and the day keeps to one line; then its amount. It opens the
// quotation.
const QuotationRow = ({ quote, currency, hover = "hover:bg-white" }) => {
  const details = [
    quote.documentNumber || "No number yet",
    quote.client || "No client",
    quote.until,
  ];
  return (
    <li>
      <Link
        to={quote.to}
        className={`${rowBase} grid-cols-[minmax(0,1fr)_auto] ${hover}`}
        aria-label={`${details.join(", ")}, ${formatMoney(quote.amount, currency)}. Open the quotation`}
      >
        <span className="min-w-0 text-[17px] [overflow-wrap:anywhere]">
          {details.map((detail, index) => (
            <Fragment key={index}>
              {index > 0 && " "}
              <span className={index === 1 ? "" : "whitespace-nowrap"}>
                {index > 0 && dot}
                {detail}
              </span>
            </Fragment>
          ))}
        </span>
        <Amount
          value={quote.amount}
          currency={currency}
          hideCode
          className={amount}
        />
      </Link>
    </li>
  );
};

// What is coming up, in the overview's currency: the next invoice each
// recurring schedule will make, the quotations still waiting for an answer,
// and the latest few whose last day went by without one, with how many more
// did. Each schedule opens the invoice it repeats, unless what needs
// attention already opens it, and each quotation its document. With nothing
// repeating yet, the way to set one up opens the recurring list, so the
// link to it beside the title gives way.
const ComingUpPanel = ({ snapshot, currencyChosen }) => {
  const { currency } = snapshot;
  const chosen = currencyChosen ? currency : undefined;
  const recurring = snapshot.comingUp.recurring || {};
  const runs = runRows(
    recurring.nextRuns,
    attentionDocuments(snapshot.attention?.items),
  );
  const next = recurring.next90Days || {};
  const { awaiting = {}, expired = {} } = snapshot.comingUp.quotations || {};
  const quotes = quotationRows(awaiting.items);
  const waiting = Number(awaiting.count) || 0;
  const lapsed = Number(expired.count) || 0;
  const lapsedQuotes = quotationRows(expired.items);
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  return (
    <section
      className={`${overviewPanel} [--rise-delay:320ms]`}
      aria-labelledby="coming-up-title"
    >
      <h2 id="coming-up-title" className={sectionTitle}>
        Coming up
      </h2>
      <div className="flex flex-col gap-2">
        <div className={partHeading}>
          <h3 className={partTitle}>
            <span
              className={`${partIcon} bg-avatar-blue text-card-blue-icon`}
              aria-hidden="true"
            >
              <FiRepeat />
            </span>
            Recurring invoices
          </h3>
          {(runs.length || recurring.active > 0 || editRefusal) && (
            <Link to={recurringLink} className={panelLink}>
              Recurring
              <FiChevronRight aria-hidden="true" />
            </Link>
          )}
        </div>
        {runs.length ? (
          <>
            <p className="text-[15px] text-spot-muted">
              Next 90 days: {countOf(next.invoices, "invoice", "invoices")} ·{" "}
              {formatMoney(next.amount, currency)}
            </p>
            <ol className={`m-0 list-none p-1 ${card}`}>
              {runs.map((run) => {
                const content = (
                  <>
                    <span className="text-[15px] text-spot-muted">
                      {run.day}
                    </span>
                    <span className="flex min-w-0 flex-col">
                      <span className="truncate text-[17px]">
                        {run.client || "No client"}
                      </span>
                      <span className="truncate text-[15px] text-spot-muted">
                        {run.number}
                      </span>
                    </span>
                    <Amount
                      value={run.amount}
                      currency={currency}
                      hideCode
                      className={amount}
                    />
                  </>
                );
                const grid = "grid-cols-[46px_minmax(0,1fr)_auto]";
                return (
                  <li key={run.scheduleId || run.templateId}>
                    {run.to ? (
                      <Link
                        to={run.to}
                        className={`${rowLink} ${grid}`}
                        aria-label={`${run.day}: ${run.client || "No client"}, ${run.number}, ${formatMoney(run.amount, currency)}. Open the invoice it repeats`}
                      >
                        {content}
                      </Link>
                    ) : (
                      <div className={`${rowBase} ${grid}`}>{content}</div>
                    )}
                  </li>
                );
              })}
            </ol>
          </>
        ) : recurring.active > 0 ? (
          <p className={`${card} p-3 text-[15px] text-spot-body`}>
            Nothing runs in the next 90 days.
          </p>
        ) : (
          <div
            className={`${card} flex flex-wrap items-center gap-x-3 gap-y-2 p-3`}
          >
            <p className="min-w-[180px] flex-1 text-[15px] text-spot-body">
              None yet. Bill a client every week, month or year without lifting
              a finger.
            </p>
            <ActionLink
              to={recurringLink}
              reason={editRefusal}
              className={panelAction}
            >
              <FiPlus aria-hidden="true" />
              Set one up
            </ActionLink>
          </div>
        )}
      </div>
      <div className="flex flex-col gap-2 border-t border-[#23273512] pt-2.5">
        <div className={partHeading}>
          <h3 className={partTitle}>
            <span
              className={`${partIcon} bg-avatar-lilac text-avatar-lilac-ink`}
              aria-hidden="true"
            >
              <FiFileText />
            </span>
            Quotations
          </h3>
          <Link to={quotationsLink(chosen)} className={panelLink}>
            All quotations
            <FiChevronRight aria-hidden="true" />
          </Link>
        </div>
        {waiting > 0 && (
          <div className="rounded-[14px] bg-white p-1">
            <p className="px-3 pt-1.5 text-[15px] text-spot-muted">
              Awaiting an answer · {waiting.toLocaleString("en")}
            </p>
            <ol className="m-0 list-none p-0">
              {quotes.map((quote) => (
                <QuotationRow
                  key={quote.documentId}
                  quote={quote}
                  currency={currency}
                  hover="hover:bg-spot-violet/[.06]"
                />
              ))}
            </ol>
            {waiting > quotes.length && (
              <p className="px-3 pb-1.5 text-[15px] text-spot-muted">
                Also{" "}
                {countOf(
                  waiting - quotes.length,
                  "more quotation",
                  "more quotations",
                )}
              </p>
            )}
          </div>
        )}
        {lapsed > 0 && (
          <div className={`${card} p-1`}>
            <p className="px-3 pt-1.5 text-[15px] text-spot-muted">
              Expired without an answer · {lapsed.toLocaleString("en")}
            </p>
            <ol className="m-0 list-none p-0">
              {lapsedQuotes.map((quote) => (
                <QuotationRow
                  key={quote.documentId}
                  quote={quote}
                  currency={currency}
                />
              ))}
            </ol>
            {lapsedQuotes.length > 0 && lapsed > lapsedQuotes.length && (
              <p className="px-3 pb-1.5 text-[15px] text-spot-muted">
                and {(lapsed - lapsedQuotes.length).toLocaleString("en")} more
              </p>
            )}
          </div>
        )}
        {!waiting && !lapsed && (
          <p className={`${card} p-3 text-[15px] text-spot-body`}>
            No quotations are waiting for an answer.
          </p>
        )}
      </div>
    </section>
  );
};

export default ComingUpPanel;
