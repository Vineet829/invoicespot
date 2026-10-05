import { Fragment } from "react";
import { Link } from "react-router-dom";
import { FiChevronRight, FiFileText, FiPlus, FiRepeat } from "react-icons/fi";
import { ActionLink, Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { countOf } from "../../../utils/counts";
import { useBusiness } from "../../business/permissions";
import {
  awaitingRows,
  expiredQuotationsLink,
  formatMoney,
  quotationsLink,
  recurringLink,
  runRows,
} from "../dashboardModel";
import { overviewPanel, panelAction, panelLink } from "./DashboardPanels";

// Each part's title, beside its icon on the tint the attention list gives
// the same kind, then the link to its list.
const partHeading = "flex items-center justify-between gap-3";
const partTitle = "flex min-w-0 items-center gap-2 text-[14px] font-medium";
const partIcon =
  "grid size-7 shrink-0 place-items-center rounded-[10px] [&>svg]:size-[15px] [&>svg]:stroke-[1.6]";
const card = "rounded-[14px] bg-white/55";
const amount = "text-[15px] font-light tracking-[-0.03em]";
// A detail's dot, kept on the line of the word after it.
const dot = "·\u00a0";
// A row that opens something, as tall on a touch screen as the overview's
// other small links.
const rowLink =
  "grid items-center gap-2.5 rounded-xl px-3 py-1.5 transition-colors hover:bg-white coarse:min-h-11";

// What is coming up, in the overview's currency: the next invoices the
// recurring schedules will make, and the quotations still waiting for an
// answer, with those whose date has passed added up. Each recurring invoice
// opens the recurring list, and each quotation its document.
const ComingUpPanel = ({ snapshot, currencyChosen }) => {
  const { currency } = snapshot;
  const chosen = currencyChosen ? currency : undefined;
  const recurring = snapshot.comingUp.recurring || {};
  const runs = runRows(recurring.nextRuns);
  const next = recurring.next90Days || {};
  const { awaiting = {}, expired = {} } = snapshot.comingUp.quotations || {};
  const quotes = awaitingRows(awaiting.items);
  const waiting = Number(awaiting.count) || 0;
  const lapsed = Number(expired.count) || 0;
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
          <Link to={recurringLink} className={panelLink}>
            Recurring
            <FiChevronRight aria-hidden="true" />
          </Link>
        </div>
        {runs.length ? (
          <>
            <p className="text-[12.5px] text-spot-muted">
              Next 90 days: {countOf(next.invoices, "invoice", "invoices")} ·{" "}
              {formatMoney(next.amount, currency)}
            </p>
            <ol className={`m-0 list-none p-1 ${card}`}>
              {runs.map((run, index) => (
                <li key={`${run.scheduleId}-${run.date}-${index}`}>
                  <Link
                    to={recurringLink}
                    className={`${rowLink} grid-cols-[46px_minmax(0,1fr)_auto]`}
                    aria-label={`${run.day}: ${run.client || "No client"}, ${run.number}, ${formatMoney(run.amount, currency)}. Open the recurring invoices`}
                  >
                    <span className="text-[12.5px] text-spot-muted">
                      {run.day}
                    </span>
                    <span className="flex min-w-0 flex-col">
                      <span className="truncate text-[13.5px]">
                        {run.client || "No client"}
                      </span>
                      <span className="truncate text-[12px] text-spot-muted">
                        {run.number}
                      </span>
                    </span>
                    <Amount
                      value={run.amount}
                      currency={currency}
                      hideCode
                      className={amount}
                    />
                  </Link>
                </li>
              ))}
            </ol>
          </>
        ) : recurring.active > 0 ? (
          <p className={`${card} p-3 text-[13px] text-spot-body`}>
            Nothing runs in the next 90 days.
          </p>
        ) : (
          <div
            className={`${card} flex flex-wrap items-center gap-x-3 gap-y-2 p-3`}
          >
            <p className="min-w-[180px] flex-1 text-[13px] text-spot-body">
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
            <p className="px-3 pt-1.5 text-[12px] text-spot-muted">
              Awaiting an answer · {waiting.toLocaleString("en")}
            </p>
            <ol className="m-0 list-none p-0">
              {quotes.map((quote) => {
                const details = [
                  quote.documentNumber || "No number yet",
                  quote.client || "No client",
                  quote.until,
                ];
                return (
                  <li key={quote.documentId}>
                    <Link
                      to={quote.to}
                      className={`${rowLink} grid-cols-[minmax(0,1fr)_auto] hover:bg-spot-violet/[.06]`}
                      aria-label={`${details.join(", ")}, ${formatMoney(quote.amount, currency)}. Open the quotation`}
                    >
                      {/* After the number, each detail starts with its dot,
                          so a wrapped line never ends on one, and the day
                          keeps to one line. */}
                      <span className="min-w-0 text-[13.5px] [overflow-wrap:anywhere]">
                        {details.map((detail, index) => (
                          <Fragment key={index}>
                            {index > 0 && " "}
                            <span
                              className={index === 1 ? "" : "whitespace-nowrap"}
                            >
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
              })}
            </ol>
            {waiting > quotes.length && (
              <p className="px-3 pb-1.5 text-[12px] text-spot-muted">
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
          <Link
            to={expiredQuotationsLink(chosen)}
            className={`${card} grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2.5 px-4 py-2.5 transition-colors hover:bg-white`}
            aria-label={`Expired without an answer: ${countOf(lapsed, "quotation", "quotations")}, ${formatMoney(expired.amount, currency)}. Open the quotations, sorted by their last valid day`}
          >
            <span className="flex min-w-0 flex-col">
              <span className="text-[12px] text-spot-muted">
                Expired without an answer · {lapsed.toLocaleString("en")}
              </span>
              <span className="text-[13.5px]">
                Worth following up, or letting go
              </span>
            </span>
            <Amount
              value={expired.amount}
              currency={currency}
              hideCode
              className={amount}
            />
          </Link>
        )}
        {!waiting && !lapsed && (
          <p className={`${card} p-3 text-[13px] text-spot-body`}>
            No quotations are waiting for an answer.
          </p>
        )}
      </div>
    </section>
  );
};

export default ComingUpPanel;
