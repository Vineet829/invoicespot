import { Link } from "react-router-dom";
import { FiChevronRight } from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { compactAmount } from "../../../utils/money";
import {
  formatMoney,
  invoiceCount,
  owedColumns,
  owedWeekLink,
  reportLink,
  wholeAmount,
} from "../dashboardModel";
import { overviewPanel, panelLink } from "./DashboardPanels";

// What clients owe now, by when it is due: overdue first, then this week,
// the two after it and later. Each column opens its invoices, and the
// report has the ageing in full, both in the same currency when one was
// chosen. The ageing is as of today, so the report's period is its own.
const OwedPanel = ({ snapshot, currencyChosen }) => {
  const { owedNow, currency } = snapshot;
  const chosen = currencyChosen ? currency : undefined;
  const columns = owedColumns(snapshot.owedByWeek);
  const grid = {
    gridTemplateColumns: `repeat(${columns.length}, minmax(0, 1fr))`,
  };
  return (
    <section
      className={`${overviewPanel} [--rise-delay:160ms]`}
      aria-labelledby="owed-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="owed-title" className={sectionTitle}>
          Invoices owed to you
        </h2>
        <Link to={reportLink("ageing", null, chosen)} className={panelLink}>
          View report
          <FiChevronRight aria-hidden="true" />
        </Link>
      </div>
      <p className="flex flex-wrap items-baseline gap-x-3">
        <Amount
          value={owedNow.total}
          currency={currency}
          hideCode
          className="text-[34px] font-extralight tracking-[-0.03em]"
        />
        <span className="text-[12.5px] text-spot-muted">
          {owedNow.invoices
            ? `across ${invoiceCount(owedNow.invoices)}`
            : "No unpaid invoices"}
        </span>
      </p>
      {owedNow.invoices > 0 ? (
        <>
          <div
            className="grid h-[196px] gap-2.5 border-b border-[#2327352e] max-[700px]:h-40"
            style={grid}
          >
            {columns.map((column, index) => {
              const label = `${column.spoken}: ${formatMoney(column.amount, currency)} on ${invoiceCount(column.invoices)}`;
              // A column is a container, so its amount can shorten to 13.4K
              // when the column is too narrow for 13,353.
              const box =
                "flex min-w-0 flex-col items-center justify-end gap-1.5 [container-type:inline-size]";
              const content = (
                <>
                  <span
                    className="whitespace-nowrap text-[13px] [@container_(width<72px)]:hidden"
                    aria-hidden="true"
                  >
                    {wholeAmount(column.amount, currency)}
                  </span>
                  <span
                    className="hidden whitespace-nowrap text-[13px] [@container_(width<72px)]:inline"
                    aria-hidden="true"
                  >
                    {compactAmount(column.amount, currency)}
                  </span>
                  <span
                    className={`w-[min(46px,70%)] origin-bottom animate-ov-column rounded-t-[4px] ${column.key === "overdue" ? "bg-owed-overdue" : "bg-owed-due"}`}
                    style={{
                      height: `calc((100% - 22px) * ${column.share})`,
                      "--column-delay": `${300 + index * 60}ms`,
                    }}
                  />
                </>
              );
              return column.invoices > 0 ? (
                <Link
                  key={column.key}
                  to={owedWeekLink(column.key, chosen)}
                  className={`${box} rounded-t-xl transition-colors hover:bg-white/60`}
                  aria-label={`${label}. Open ${column.key === "overdue" ? "the overdue invoices" : "these invoices"}`}
                >
                  {content}
                </Link>
              ) : (
                <div
                  key={column.key}
                  className={box}
                  role="img"
                  aria-label={label}
                >
                  {content}
                </div>
              );
            })}
          </div>
          <div className="grid gap-2.5" style={grid} aria-hidden="true">
            {columns.map((column) => (
              <span className="text-center leading-[1.3]" key={column.key}>
                <span className="block text-[13px] text-spot-ink">
                  {column.name}
                </span>
                <span className="block text-[11.5px] text-spot-muted">
                  <span className="whitespace-nowrap">{column.when}</span>{" "}
                  <span className="whitespace-nowrap">· {column.invoices}</span>
                </span>
              </span>
            ))}
          </div>
          <p className="mt-auto border-t border-[#23273512] pt-2.5 text-[12px] text-spot-muted">
            Grouped by due date. Each column opens its invoices.
          </p>
        </>
      ) : (
        <p className="grid flex-1 place-items-center py-10 text-center text-[13px] text-spot-muted">
          Nothing is owed to you right now.
        </p>
      )}
    </section>
  );
};

export default OwedPanel;
