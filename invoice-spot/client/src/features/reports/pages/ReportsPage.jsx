import { Link, useSearchParams } from "react-router-dom";
import {
  FiArrowDownLeft,
  FiBarChart2,
  FiClock,
  FiDownload,
  FiPlus,
  FiTrendingUp,
} from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useFileDownload from "../../../hooks/useFileDownload";
import {
  ActionLink,
  Amount,
  DataTable,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  dateLabel,
} from "../../../components/workspace/Workspace";
import {
  filterChip,
  panelNote,
  rowAction,
  textLink,
  toolbarSelect,
} from "../../../components/workspace/pastel";
import {
  FinancialCard,
  financeCards,
} from "../../dashboard/components/DashboardPanels";
import { formatMoney } from "../../dashboard/dashboardModel";
import { useGetReportCsvMutation, useGetReportQuery } from "../reportsApiSlice";
import {
  DEFAULT_PERIOD,
  monthYear,
  periodOptions,
  share,
} from "../reportModel";
import IncomeChart, { SERIES } from "../components/IncomeChart";

const AGEING_BARS = {
  notDue: "bg-spot-lilac",
  days1to30: "bg-spot-coral/50",
  days31to60: "bg-spot-coral/70",
  days61to90: "bg-spot-coral/85",
  over90: "bg-overdue-icon",
};

// A row's share of the largest row, drawn under its name. The figures are
// in the row's other cells.
const ShareBar = ({ ratio, tone }) => (
  <span
    className="mt-2 block h-1.5 w-full max-w-[240px] overflow-hidden rounded-full bg-[#23273510]"
    aria-hidden="true"
  >
    <span
      className={`block h-full origin-left animate-ov-bar rounded-[inherit] ${tone}`}
      style={{ width: `${ratio * 100}%` }}
    />
  </span>
);

// Downloads one section for every currency, named like the server names it.
const CsvButton = ({ section, report, name, fileName }) => {
  const [download, busy] = useFileDownload(useGetReportCsvMutation);
  return (
    <button
      type="button"
      className={rowAction}
      onClick={() =>
        download(
          { section, period: report.period },
          `invoice-spot-${fileName}.csv`,
        )
      }
      disabled={busy}
      aria-label={`Download ${name} as CSV`}
    >
      <FiDownload aria-hidden="true" /> CSV
    </button>
  );
};

const amountColumn = (label, key, currency) => ({
  label,
  type: "amount",
  render: (row) => <Amount value={row[key]} currency={currency} />,
});

const ReportsPage = () => {
  useTitle("Reports — Invoice Spot");
  const [params, setParams] = useSearchParams();
  const period = params.get("period") || DEFAULT_PERIOD;
  const query = useGetReportQuery(period);
  const report = query.data;
  const currencies = report?.currencies || [];
  const shown =
    currencies.find((entry) => entry.currency === params.get("currency")) ||
    currencies[0];
  const currency = shown?.currency;
  // The address keeps what was chosen; the default period and a currency
  // nobody picked stay out of it.
  const choose = (changes) =>
    setParams(
      Object.fromEntries(
        Object.entries({
          period,
          currency: params.get("currency"),
          ...changes,
        }).filter(
          ([key, value]) =>
            value && !(key === "period" && value === DEFAULT_PERIOD),
        ),
      ),
      { replace: true },
    );
  const options = periodOptions(report?.years);
  if (!options.some((option) => option.value === period)) {
    options.push({ value: period, label: period });
  }
  const periodName = report
    ? `${dateLabel(report.from)} – ${dateLabel(report.to)}`
    : "";
  const periodFile = report ? `${report.from}-to-${report.to}` : "";

  const largestAge = Math.max(
    0,
    ...(shown?.ageing || []).map((row) => Number(row.outstanding)),
  );
  const largestMethod = Math.max(
    0,
    ...(shown?.methods || []).map((row) => Number(row.amount)),
  );
  const largestClient = Number(shown?.clients[0]?.billed) || 0;
  const clientRows = shown
    ? [
        ...shown.clients,
        ...(shown.otherClients
          ? [{ ...shown.otherClients, others: true }]
          : []),
      ]
    : [];

  return (
    <WorkspacePage section="Reports">
      <PageHeader
        title="Reports"
        description="Billed, received and owed, from your invoices, receipts and payments."
        actions={
          <>
            {currencies.length > 1 && (
              <fieldset className="m-0 flex min-w-0 flex-wrap gap-2 border-0 p-0">
                <legend className="sr-only">Currency</legend>
                {currencies.map((entry) => (
                  <label key={entry.currency} className={filterChip}>
                    <input
                      type="radio"
                      name="report-currency"
                      className="sr-only"
                      checked={entry.currency === currency}
                      onChange={() => choose({ currency: entry.currency })}
                    />
                    {entry.currency || "No currency"}
                  </label>
                ))}
              </fieldset>
            )}
            <label className="flex items-center gap-2.5">
              <span className="sr-only">Period</span>
              <select
                className={toolbarSelect}
                value={period}
                onChange={(event) => choose({ period: event.target.value })}
              >
                {options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </>
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.isFetching ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : !shown ? (
        <Panel>
          <EmptyState
            title="Nothing to report for this period."
            text="Reports fill in as you issue invoices and receipts and record payments. Try a longer period, or create a document."
            icon={FiBarChart2}
            action={
              <ActionLink to="/create-doc">
                <FiPlus aria-hidden="true" /> Create document
              </ActionLink>
            }
          />
        </Panel>
      ) : (
        <>
          <section
            className={`${financeCards} transition-opacity ${query.isFetching ? "opacity-60" : ""}`}
            aria-label={`Totals in ${currency}`}
            aria-busy={query.isFetching || undefined}
          >
            <FinancialCard
              label="Billed"
              value={formatMoney(shown.billed, currency)}
              detail={`${currency} · invoices and receipts issued`}
              icon={FiTrendingUp}
              tone="blue"
            />
            <FinancialCard
              label="Received"
              value={formatMoney(shown.received, currency)}
              detail={`${currency} · payments and receipts`}
              icon={FiArrowDownLeft}
              tone="lilac"
            />
            <FinancialCard
              label="Owed now"
              value={formatMoney(shown.outstanding, currency)}
              detail={`${currency} · ${Number(shown.overdue) > 0 ? `${formatMoney(shown.overdue, currency)} of it` : "none of it"} overdue`}
              icon={FiClock}
              tone="peach"
            />
          </section>
          <Panel
            title="Income by month"
            description={`${periodName}. Invoices and receipts count on the day they were issued, payments on the day they were paid.`}
            aside={
              <div className="flex shrink-0 flex-wrap items-center gap-x-5 gap-y-2">
                <ul className="m-0 flex list-none gap-4 p-0">
                  {SERIES.map((series) => (
                    <li
                      key={series.key}
                      className={`flex items-center gap-2 ${panelNote}`}
                    >
                      <span
                        className={`size-2.5 rounded-full ${series.dot}`}
                        aria-hidden="true"
                      />
                      {series.label}
                    </li>
                  ))}
                </ul>
                <CsvButton
                  section="income"
                  report={report}
                  name="income by month"
                  fileName={`income-${periodFile}`}
                />
              </div>
            }
          >
            <IncomeChart months={shown.months} currency={currency} />
            <details className="group mt-4">
              <summary className={`w-max cursor-pointer ${textLink}`}>
                Show the figures
              </summary>
              <div className="mt-3">
                <DataTable
                  label={`Billed and received by month, ${currency}`}
                  narrow
                  rows={shown.months}
                  rowKey={(row) => row.month}
                  columns={[
                    {
                      label: "Month",
                      type: "main",
                      render: (row) => monthYear(row.month),
                    },
                    amountColumn("Billed", "billed", currency),
                    amountColumn("Received", "received", currency),
                  ]}
                />
              </div>
            </details>
          </Panel>
          <div className="grid min-w-0 gap-6 compact:gap-5 min-[1100px]:grid-cols-2 max-[700px]:gap-5">
            <Panel
              title="Unpaid invoices by age"
              description={`As of ${dateLabel(report.today)}, by days past the due date.`}
              aside={
                <CsvButton
                  section="ageing"
                  report={report}
                  name="unpaid invoices by age"
                  fileName={`unpaid-by-age-${report.today}`}
                />
              }
            >
              <DataTable
                label={`Unpaid invoices by age, ${currency}`}
                narrow
                rows={shown.ageing}
                rowKey={(row) => row.key}
                columns={[
                  {
                    label: "Age",
                    type: "main",
                    render: (row) => (
                      <>
                        {row.label}
                        <ShareBar
                          ratio={share(row.outstanding, largestAge)}
                          tone={AGEING_BARS[row.key]}
                        />
                      </>
                    ),
                  },
                  {
                    label: "Invoices",
                    render: (row) => row.invoices.toLocaleString("en"),
                  },
                  amountColumn("Owed", "outstanding", currency),
                ]}
              />
            </Panel>
            <Panel
              title="Payment methods"
              description="How invoice payments in this period were made."
              aside={
                <CsvButton
                  section="methods"
                  report={report}
                  name="payment methods"
                  fileName={`payment-methods-${periodFile}`}
                />
              }
            >
              {shown.methods.length ? (
                <DataTable
                  label={`Payment methods, ${currency}`}
                  narrow
                  rows={shown.methods}
                  rowKey={(row) => row.method}
                  columns={[
                    {
                      label: "Method",
                      type: "main",
                      render: (row) => (
                        <>
                          {row.method}
                          <ShareBar
                            ratio={share(row.amount, largestMethod)}
                            tone="bg-card-lilac-bar"
                          />
                        </>
                      ),
                    },
                    {
                      label: "Payments",
                      render: (row) => row.payments.toLocaleString("en"),
                    },
                    amountColumn("Amount", "amount", currency),
                  ]}
                />
              ) : (
                <EmptyState
                  title="No payments in this period."
                  text="Payments recorded on invoices show up here by method."
                  icon={FiArrowDownLeft}
                />
              )}
            </Panel>
          </div>
          <Panel
            title="Top clients"
            description="By the amount billed in this period, with what they have paid on it so far. The CSV file lists every client."
            aside={
              <CsvButton
                section="clients"
                report={report}
                name="clients"
                fileName={`clients-${periodFile}`}
              />
            }
          >
            {clientRows.length ? (
              <DataTable
                label={`Top clients, ${currency}`}
                rows={clientRows}
                rowKey={(row) =>
                  row.others ? "others" : row.customerId || `name-${row.name}`
                }
                columns={[
                  {
                    label: "Client",
                    type: "main",
                    render: (row) =>
                      row.others ? (
                        <span className="font-normal text-spot-muted">
                          {row.clients.toLocaleString("en")} other{" "}
                          {row.clients === 1 ? "client" : "clients"}
                        </span>
                      ) : (
                        <>
                          {row.customerId ? (
                            <Link
                              className={textLink}
                              to={`/single-customer/${row.customerId}`}
                            >
                              {row.name}
                            </Link>
                          ) : (
                            row.name || "Unnamed client"
                          )}
                          <ShareBar
                            ratio={share(row.billed, largestClient)}
                            tone="bg-card-blue-bar"
                          />
                        </>
                      ),
                  },
                  {
                    label: "Documents",
                    render: (row) => row.documents.toLocaleString("en"),
                  },
                  amountColumn("Billed", "billed", currency),
                  amountColumn("Paid", "paid", currency),
                  amountColumn("Owed", "outstanding", currency),
                ]}
              />
            ) : (
              <EmptyState
                title="No invoices or receipts in this period."
                text="Clients show up here once you bill them."
                icon={FiTrendingUp}
              />
            )}
          </Panel>
        </>
      )}
    </WorkspacePage>
  );
};

export default ReportsPage;
