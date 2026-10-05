import { Link } from "react-router-dom";
import { FiChevronRight, FiPlus } from "react-icons/fi";
import { ActionLink, Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { countOf } from "../../../utils/counts";
import { useBusiness } from "../../business/permissions";
import {
  findPeriod,
  formatMoney,
  recordExpenseLink,
  spendingLink,
  spendingRows,
} from "../dashboardModel";
import { overviewPanel, panelAction, panelLink } from "./DashboardPanels";

// A row: the category and its count above its bar, then its amount, which
// can widen the column, and its share.
const row =
  "grid grid-cols-[minmax(0,1fr)_minmax(92px,auto)_44px] items-center gap-2.5 py-[7px]";

// What was spent in the period shown, as the Profit tile takes it off
// income, by category, largest first. The figures count one currency, the
// overview's, so every link keeps to it, even when the address names none:
// each category opens its expenses for the same period, All expenses every
// one, and Record an expense the add dialog over them. The categories past
// the largest few are added up in one row, which opens nothing.
const SpendingPanel = ({ snapshot }) => {
  const { spending, currency } = snapshot;
  const period = findPeriod(snapshot.period.key);
  const rows = spendingRows(spending);
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  return (
    <section
      className={`${overviewPanel} [--rise-delay:320ms]`}
      aria-labelledby="spending-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="spending-title" className={sectionTitle}>
          Spending
        </h2>
        <Link to={spendingLink(period.key, currency)} className={panelLink}>
          All expenses
          <FiChevronRight aria-hidden="true" />
        </Link>
      </div>
      <p className="flex flex-wrap items-baseline gap-x-2.5">
        <Amount
          value={spending.total}
          currency={currency}
          hideCode
          className="text-[30px] font-extralight tracking-[-0.03em]"
        />
        <span className="text-[12.5px] text-spot-muted">
          spent {period.inThese}
        </span>
      </p>
      {rows.length ? (
        <>
          <ol className="m-0 list-none p-0">
            {rows.map((entry, index) => {
              const name = entry.others
                ? countOf(
                    entry.categories,
                    "other category",
                    "other categories",
                  )
                : entry.category;
              const label = `${name}: ${formatMoney(entry.amount, currency)} on ${countOf(entry.expenses, "expense", "expenses")}, ${entry.share} of spending`;
              const content = (
                <>
                  <span className="flex min-w-0 flex-col gap-1">
                    {/* A long name shortens; its count stays in sight. */}
                    <span className="flex min-w-0 items-baseline gap-1 text-[13.5px]">
                      <span
                        className={`truncate ${entry.others ? "text-spot-muted" : ""}`}
                      >
                        {name}
                      </span>{" "}
                      <span className="shrink-0 text-[12px] text-spot-muted">
                        · {Number(entry.expenses).toLocaleString("en")}
                      </span>
                    </span>
                    <span className="block h-1.5 overflow-hidden rounded-full bg-flow-out/[.14]">
                      <span
                        className="block h-full origin-left animate-ov-bar rounded-[inherit] bg-flow-out"
                        style={{ width: `${entry.bar * 100}%` }}
                      />
                    </span>
                  </span>
                  <Amount
                    value={entry.amount}
                    currency={currency}
                    hideCode
                    className="justify-self-end text-[15px] font-light tracking-[-0.03em]"
                  />
                  <span className="text-right text-[12px] text-spot-muted">
                    {entry.share}
                  </span>
                </>
              );
              return (
                <li
                  className={index ? "border-t border-[#23273512]" : ""}
                  key={entry.others ? "others" : `category-${entry.category}`}
                >
                  {entry.others ? (
                    <div className={row} role="img" aria-label={label}>
                      {content}
                    </div>
                  ) : (
                    <Link
                      to={spendingLink(period.key, currency, entry.category)}
                      className={`${row} -mx-2 rounded-xl px-2 transition-colors hover:bg-white/50`}
                      aria-label={`${label}. Open these expenses`}
                    >
                      {content}
                    </Link>
                  )}
                </li>
              );
            })}
          </ol>
          <p className="mt-auto border-t border-[#23273512] pt-2 text-[12px] text-spot-muted">
            Each category opens its expenses for {period.these}.
          </p>
        </>
      ) : (
        <>
          <div className="flex min-h-[200px] flex-1 flex-col items-center justify-center gap-3 rounded-[18px] bg-white/50 p-3 text-center">
            <span
              className="size-11 rounded-full border-[7px] border-dashed border-[#23273524]"
              aria-hidden="true"
            />
            <p className="text-[13.5px] text-spot-body">
              No expenses recorded {period.inThese}.
            </p>
            <ActionLink
              to={recordExpenseLink(currency)}
              reason={editRefusal}
              className={panelAction}
            >
              <FiPlus aria-hidden="true" />
              Record an expense
            </ActionLink>
          </div>
          <p className="border-t border-[#23273512] pt-2 text-[12px] text-spot-muted">
            Expenses by category, each opening its expenses.
          </p>
        </>
      )}
    </section>
  );
};

export default SpendingPanel;
