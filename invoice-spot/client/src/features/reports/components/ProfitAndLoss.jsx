import { FiTrendingUp } from "react-icons/fi";
import {
  Amount,
  DataTable,
  EmptyState,
  Panel,
  SHARE_BAR_TRACK,
  ShareBar,
  money,
} from "../../../components/workspace/Workspace";
import {
  monthYear,
  percentLabel,
  percentOf,
  profitMonths,
  share,
} from "../reportModel";

const amountColumn = (label, key, currency) => ({
  label,
  type: "amount",
  render: (row) => <Amount value={row[key]} currency={currency} />,
});

// Profit and loss for the period, in each currency it has: income before tax
// less credit notes, and what expenses cost, by month, with each month's
// margin and the profit made so far; then what each expense category cost,
// and its share of all the expenses.
const ProfitAndLoss = ({ report, periodName, busy }) => {
  const currencies = report?.currencies || [];
  return (
    <Panel
      id="profit"
      title="Profit and loss"
      description={`${periodName}. Income is what you invoiced before tax, less credit notes, on the day each was issued. Expenses count on the day they were paid, with any tax you can’t claim back.`}
    >
      {currencies.length ? (
        <div
          className={`flex flex-col gap-8 transition-opacity ${busy ? "opacity-60" : ""}`}
          aria-busy={busy || undefined}
        >
          {currencies.map((entry) => (
            <section
              key={entry.currency}
              aria-label={`Profit and loss in ${entry.currency || "no currency"}`}
            >
              <p className="mb-5 text-[16px] text-spot-body">
                Income{" "}
                <span className="text-spot-ink">
                  {money(entry.income, entry.currency)}
                </span>
                , expenses{" "}
                <span className="text-spot-ink">
                  {money(entry.expenses, entry.currency)}
                </span>
                : {Number(entry.profit) < 0 ? "a loss of" : "a profit of"}{" "}
                <span className="text-spot-ink">
                  {money(Math.abs(Number(entry.profit)), entry.currency)}
                </span>
                .
              </p>
              <div className="flex min-w-0 flex-col gap-6">
                <DataTable
                  evenGaps
                  large
                  label={`Profit and loss by month, ${entry.currency}`}
                  narrow
                  rows={profitMonths(entry.months)}
                  rowKey={(row) => row.month}
                  columns={[
                    {
                      label: "Month",
                      type: "main",
                      render: (row) => monthYear(row.month),
                    },
                    amountColumn("Income", "income", entry.currency),
                    amountColumn("Expenses", "expenses", entry.currency),
                    amountColumn("Profit", "profit", entry.currency),
                    {
                      label: "Margin",
                      type: "number",
                      render: (row) => percentLabel(row.margin),
                    },
                    amountColumn("Profit so far", "soFar", entry.currency),
                  ]}
                />
                {entry.categories.length > 0 && (
                  <DataTable
                    evenGaps
                    large
                    label={`Expenses by category, ${entry.currency}`}
                    narrow
                    rows={entry.categories}
                    rowKey={(row) => row.category}
                    columns={[
                      {
                        label: "Category",
                        type: "main",
                        track: SHARE_BAR_TRACK,
                        render: (row) => (
                          <>
                            {row.category}
                            <ShareBar
                              ratio={share(
                                row.expenses,
                                entry.categories[0].expenses,
                              )}
                              tone="bg-flow-out"
                            />
                          </>
                        ),
                      },
                      amountColumn("Expenses", "expenses", entry.currency),
                      {
                        label: "Share of expenses",
                        type: "number",
                        render: (row) =>
                          percentLabel(
                            percentOf(row.expenses, entry.expenses),
                            row.expenses,
                          ),
                      },
                    ]}
                  />
                )}
              </div>
            </section>
          ))}
        </div>
      ) : (
        <EmptyState
          title="No income or expenses in this period."
          text="Issue invoices or receipts, or record expenses, and they show up here."
          icon={FiTrendingUp}
        />
      )}
    </Panel>
  );
};

export default ProfitAndLoss;
