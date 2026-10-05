import { Link } from "react-router-dom";
import { FiChevronRight } from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { findPeriod, formatMoney, topItemRows } from "../dashboardModel";
import { overviewPanel, panelLink } from "./DashboardPanels";

// A row: the item, its bar, how many were sold and its share of income,
// then what it brought in, which can widen the column.
const row =
  "grid grid-cols-[minmax(0,1fr)_minmax(96px,auto)] items-center gap-2.5 py-[7px]";

const sold = (quantity) =>
  `${Number(quantity || 0).toLocaleString("en", { maximumFractionDigits: 2 })} sold`;

// What sold the most in the period shown, in the overview's currency, as
// income counts it: before tax, after discounts, less what credit notes
// took back. Each row opens the Items list searched for its name, which
// need not be a saved item's, and All items the whole list. The rest of the
// lines aren't shown, so the shares add up to less than all the income.
const TopItemsPanel = ({ snapshot }) => {
  const { currency } = snapshot;
  const period = findPeriod(snapshot.period.key);
  const rows = topItemRows(snapshot.topItems, snapshot.itemsTotal);
  return (
    <section
      className={`${overviewPanel} [--rise-delay:320ms]`}
      aria-labelledby="top-items-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="top-items-title" className={sectionTitle}>
          Top items
        </h2>
        <Link to="/items" className={panelLink}>
          All items
          <FiChevronRight aria-hidden="true" />
        </Link>
      </div>
      {rows.length ? (
        <>
          <p className="text-[12.5px] text-spot-muted">
            What you sold {period.inThese}, before tax
          </p>
          <ol className="m-0 list-none p-0">
            {rows.map((entry, index) => {
              const facts = [
                sold(entry.quantity),
                entry.share && `${entry.share} of income`,
              ].filter(Boolean);
              const label = `${entry.name}: ${formatMoney(entry.amount, currency)}, ${facts.join(", ")}`;
              const content = (
                <>
                  <span className="flex min-w-0 flex-col gap-1">
                    <span
                      className={`truncate text-[13.5px] ${entry.to ? "" : "text-spot-muted"}`}
                    >
                      {entry.name}
                    </span>
                    <span className="block h-1.5 overflow-hidden rounded-full bg-card-sage-bar/[.16]">
                      <span
                        className="block h-full origin-left animate-ov-bar rounded-[inherit] bg-card-sage-bar"
                        style={{ width: `${entry.bar * 100}%` }}
                      />
                    </span>
                    <span className="text-[11.5px] text-spot-muted">
                      {facts.join(" · ")}
                    </span>
                  </span>
                  <Amount
                    value={entry.amount}
                    currency={currency}
                    hideCode
                    className="justify-self-end text-[15px] font-light tracking-[-0.03em]"
                  />
                </>
              );
              return (
                <li
                  className={index ? "border-t border-[#23273512]" : ""}
                  key={`${index}-${entry.name}`}
                >
                  {entry.to ? (
                    <Link
                      to={entry.to}
                      className={`${row} -mx-2 rounded-xl px-2 transition-colors hover:bg-white/50`}
                      aria-label={`${label}. Search Items for it`}
                    >
                      {content}
                    </Link>
                  ) : (
                    <div className={row} role="img" aria-label={label}>
                      {content}
                    </div>
                  )}
                </li>
              );
            })}
          </ol>
          <p className="mt-auto border-t border-[#23273512] pt-2 text-[12px] text-spot-muted">
            Everything sold adds up to the income behind Profit.
            {rows.some((entry) => entry.to) &&
              " Each row searches Items for it."}
          </p>
        </>
      ) : (
        <div className="flex min-h-[200px] flex-1 flex-col items-center justify-center gap-3 rounded-[18px] bg-white/50 p-3 text-center">
          <span
            className="size-11 rounded-full border-[7px] border-dashed border-[#23273524]"
            aria-hidden="true"
          />
          <p className="text-[13.5px] text-spot-body">
            Nothing sold {period.inThese}.
          </p>
        </div>
      )}
    </section>
  );
};

export default TopItemsPanel;
