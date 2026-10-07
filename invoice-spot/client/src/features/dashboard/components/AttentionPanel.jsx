import { Link } from "react-router-dom";
import { FiCheckCircle } from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import {
  attentionFooter,
  attentionItems,
  attentionRow,
  formatMoney,
} from "../dashboardModel";
import { overviewPanel } from "./DashboardPanels";

// Each kind's colour, for its heading and its state.
const TINTS = {
  paymentReport: "text-avatar-sage-ink",
  overdue: "text-overdue-strong",
  dueSoon: "text-avatar-blue-ink",
  draft: "text-spot-muted",
};

// A row reads as Top clients' rows do: the client, a bar of how late an
// overdue invoice is beside the most overdue one listed, then the
// document's number, its day and its state, with the amount on the right.
// Names and numbers wrap rather than push the amount out.
const row =
  "relative -mx-2 grid grid-cols-[minmax(0,1fr)_minmax(96px,auto)] items-center gap-2.5 rounded-xl px-2 py-[7px] text-spot-ink transition-colors";
// The row's own link, to its document, covers the whole row and shows its
// focus around it. Its name reads the whole row, so the words beside it are
// hidden from screen readers rather than read twice.
const documentLink =
  "line-clamp-2 text-[17px] font-medium [overflow-wrap:anywhere] after:absolute after:inset-0 after:rounded-xl after:content-[''] focus-visible:outline-none focus-visible:after:outline focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[#5b47a0]";

// How late an overdue invoice is, in days, or nothing for the other kinds.
const daysLate = (item) =>
  item.kind === "overdue" && Number(item.days) > 0 ? Number(item.days) : 0;

// The rows in runs of one kind, in the order the list gives them, so each
// kind is named once, above its rows.
const kindGroups = (items) =>
  items.reduce((groups, item) => {
    const last = groups.at(-1);
    if (last?.[0].kind === item.kind) last.push(item);
    else groups.push([item]);
    return groups;
  }, []);

// The few invoices that need doing now, and drafts to issue, most urgent
// first, each opening its document. Quotations and recurring invoices are
// in Coming up, the client is one click away on the document, and the
// Overdue figure above opens every overdue invoice, so nothing here opens
// what another panel opens. A document listed twice, such as an overdue
// invoice with a payment reported on it, opens from its first row only. The
// note names what there is none of, and what the list leaves out.
const AttentionPanel = ({ snapshot }) => {
  const { attention, currency } = snapshot;
  const items = attentionItems(attention);
  const footer = attentionFooter(attention?.counts, items);
  const opened = new Set();
  return (
    <section
      className={`${overviewPanel} [--rise-delay:240ms]`}
      aria-labelledby="attention-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="attention-title" className={sectionTitle}>
          Needs your attention
        </h2>
      </div>
      {items.length ? (
        <>
          <div className="flex flex-col gap-3">
            {kindGroups(items).map((group, run) => {
              const mostLate = Math.max(...group.map(daysLate));
              return (
                <div key={`${group[0].kind}-${run}`}>
                  <h3
                    className={`m-0 text-[15px] font-medium ${TINTS[group[0].kind]}`}
                  >
                    {attentionRow(group[0]).label}
                  </h3>
                  <ol className="m-0 list-none p-0">
                    {group.map((item, index) => {
                      const { label, state, when, to } = attentionRow(item);
                      const late = daysLate(item);
                      const number = item.documentNumber || "No number yet";
                      const client = item.client || "No client";
                      const name = [
                        `${label} ${number}`.trim(),
                        client,
                        state,
                        when,
                        formatMoney(item.amount, currency),
                      ]
                        .filter(Boolean)
                        .join(", ");
                      const opens = !opened.has(item.documentId);
                      opened.add(item.documentId);
                      return (
                        <li
                          className={index ? "border-t border-[#23273512]" : ""}
                          key={`${item.documentId}-${index}`}
                        >
                          <div
                            className={`${row} ${opens ? "hover:bg-white/50" : ""}`}
                            role={opens ? undefined : "img"}
                            aria-label={opens ? undefined : name}
                          >
                            <span className="flex min-w-0 flex-col gap-1">
                              {opens ? (
                                <Link
                                  to={to}
                                  className={documentLink}
                                  aria-label={name}
                                >
                                  {client}
                                </Link>
                              ) : (
                                <span className="line-clamp-2 text-[17px] font-medium [overflow-wrap:anywhere]">
                                  {client}
                                </span>
                              )}
                              {late > 0 && (
                                <span
                                  className="block h-1.5 overflow-hidden rounded-full bg-owed-overdue/[.14]"
                                  aria-hidden="true"
                                >
                                  <span
                                    className="block h-full origin-left animate-ov-bar rounded-[inherit] bg-owed-overdue"
                                    style={{
                                      // The least late still shows a sliver.
                                      width: `${Math.max(late / mostLate, 0.03) * 100}%`,
                                    }}
                                  />
                                </span>
                              )}
                              <span
                                className="text-[15px] text-spot-muted"
                                aria-hidden="true"
                              >
                                <span className="text-spot-ink">{number}</span>
                                {when && (
                                  <>
                                    {" · "}
                                    <span className="whitespace-nowrap">
                                      {when}
                                    </span>
                                  </>
                                )}
                                {state && (
                                  <>
                                    {" · "}
                                    <span
                                      className={`whitespace-nowrap ${TINTS[item.kind]}`}
                                    >
                                      {state}
                                    </span>
                                  </>
                                )}
                              </span>
                            </span>
                            <span
                              className="justify-self-end"
                              aria-hidden="true"
                            >
                              <Amount
                                value={item.amount}
                                currency={currency}
                                hideCode
                                className="text-[20px] font-light leading-tight tracking-[-0.03em] tabular-nums"
                              />
                            </span>
                          </div>
                        </li>
                      );
                    })}
                  </ol>
                </div>
              );
            })}
          </div>
          {footer && (
            <p className="mt-auto border-t border-[#23273512] pt-2 text-[15px] text-spot-muted">
              {footer}
            </p>
          )}
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-10 text-center">
          <span
            className="grid size-11 place-items-center rounded-2xl bg-avatar-sage text-avatar-sage-ink [&>svg]:size-5 [&>svg]:stroke-[1.6]"
            aria-hidden="true"
          >
            <FiCheckCircle />
          </span>
          <p className="font-display text-[20px] font-light">
            Nothing needs your attention right now.
          </p>
        </div>
      )}
    </section>
  );
};

export default AttentionPanel;
