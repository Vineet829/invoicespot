import { FiTrendingUp } from "react-icons/fi";
import {
  Amount,
  DataTable,
  EmptyState,
  Panel,
  money,
} from "../../../components/workspace/Workspace";
import { monthYear } from "../reportModel";

const amountColumn = (label, key, currency) => ({
  label,
  type: "amount",
  render: (row) => <Amount value={row[key]} currency={currency} />,
});

// Profit and loss for the period, in each currency it has: income before tax
// less credit notes, and what expenses cost, by month and by category.
const ProfitAndLoss = ({ report, periodName, busy }) => {
  const currencies = report?.currencies || [];
  return (
    <Panel
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
              <p className="mb-5 text-[14px] text-spot-body">
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
              <div className="grid min-w-0 gap-6 min-[1100px]:grid-cols-2">
                <DataTable
                  label={`Profit and loss by month, ${entry.currency}`}
                  narrow
                  rows={entry.months}
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
                  ]}
                />
                {entry.categories.length > 0 && (
                  <DataTable
                    label={`Expenses by category, ${entry.currency}`}
                    narrow
                    rows={entry.categories}
                    rowKey={(row) => row.category}
                    columns={[
                      {
                        label: "Category",
                        type: "main",
                        render: (row) => row.category,
                      },
                      amountColumn("Expenses", "expenses", entry.currency),
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
