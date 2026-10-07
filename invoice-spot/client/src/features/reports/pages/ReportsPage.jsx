import { useEffect } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import {
  FiArrowDownLeft,
  FiBarChart2,
  FiClock,
  FiDownload,
  FiPercent,
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
  ShareBar,
  WorkspacePage,
  dateLabel,
  money,
} from "../../../components/workspace/Workspace";
import {
  filterChip,
  panelNote,
  rowAction,
  textLink,
} from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";
import {
  FinancialCard,
  financeCards,
} from "../../dashboard/components/DashboardPanels";
import { formatMoney } from "../../dashboard/dashboardModel";
import {
  useGetReportCsvMutation,
  useGetReportQuery,
  useGetTaxReportQuery,
  useGetTdsReportQuery,
  useGetProfitReportQuery,
} from "../reportsApiSlice";
import {
  DEFAULT_PERIOD,
  monthYear,
  periodOptions,
  share,
} from "../reportModel";
import IncomeChart, { SERIES } from "../components/IncomeChart";
import GstHelpers from "../components/GstHelpers";
import ProfitAndLoss from "../components/ProfitAndLoss";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";

const AGEING_BARS = {
  notDue: "bg-spot-lilac",
  days1to30: "bg-spot-coral/50",
  days31to60: "bg-spot-coral/70",
  days61to90: "bg-spot-coral/85",
  over90: "bg-overdue-icon",
};

// Downloads one section for every currency, or in the reporting currency
// as the report shows it, named like the server names it. The tax withheld
// is only ever by currency.
const CsvButton = ({ section, report, name, fileName }) => {
  const [download, busy] = useFileDownload(useGetReportCsvMutation);
  const converted = report.view === "base" && section !== "tds";
  return (
    <button
      type="button"
      className={rowAction}
      onClick={() =>
        download(
          {
            section,
            period: report.period,
            ...(converted && { view: "base" }),
          },
          `invoice-spot-${fileName}${converted ? `-in-${report.baseCurrency}` : ""}.csv`,
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

// What was taxed and charged at a rate; under GST, split as it was charged.
const taxColumns = (currency, gst) => [
  amountColumn("Taxable", "taxable", currency),
  ...(gst
    ? [
        amountColumn("CGST", "cgst", currency),
        amountColumn("SGST/UTGST", "sgst", currency),
        amountColumn("IGST", "igst", currency),
      ]
    : []),
  amountColumn("Tax", "tax", currency),
];

// The tax charged in the period at each rate, less credit notes, with each
// month's figures behind a disclosure. Supplies under reverse charge carry no
// tax the business charged, so they are only mentioned.
const TaxCharged = ({ report, entry, currency, periodFile, busy }) => (
  <Panel
    title="Tax charged"
    description="Tax on the invoices and receipts issued in this period, at each rate, less credit notes. The CSV file has each month."
    aside={
      <CsvButton
        section="tax"
        report={report}
        name="tax charged"
        fileName={`tax-by-rate-${periodFile}`}
      />
    }
  >
    {entry ? (
      <div
        className={`transition-opacity ${busy ? "opacity-60" : ""}`}
        aria-busy={busy || undefined}
      >
        <p className="mb-5 text-[14px] text-spot-body">
          <span className="text-spot-ink">{money(entry.tax, currency)}</span>{" "}
          charged on {money(entry.taxable, currency)}, from{" "}
          {entry.documents.toLocaleString("en")}{" "}
          {entry.documents === 1 ? "document" : "documents"}.
        </p>
        <DataTable
          label={`Tax charged by rate, ${currency}`}
          rows={entry.rates}
          rowKey={(row) => row.rate}
          columns={[
            {
              label: "Rate",
              type: "main",
              render: (row) => `${row.rate}%`,
            },
            {
              label: "Documents",
              render: (row) => row.documents.toLocaleString("en"),
            },
            ...taxColumns(currency, entry.gst),
          ]}
        />
        {Number(entry.reverseChargeTaxable) !== 0 && (
          <p className={`mt-4 ${panelNote}`}>
            Supplies worth {money(entry.reverseChargeTaxable, currency)} were
            under reverse charge: your clients pay their tax, so none of it is
            counted here.
          </p>
        )}
        <details className="group mt-4">
          <summary className={`w-max cursor-pointer ${textLink}`}>
            Show each month
          </summary>
          <div className="mt-3">
            <DataTable
              label={`Tax charged by month and rate, ${currency}`}
              rows={entry.months}
              rowKey={(row) => `${row.month}-${row.rate}`}
              columns={[
                {
                  label: "Month",
                  type: "main",
                  render: (row) => monthYear(row.month),
                },
                { label: "Rate", render: (row) => `${row.rate}%` },
                ...taxColumns(currency, entry.gst),
              ]}
            />
          </div>
        </details>
      </div>
    ) : (
      <EmptyState
        title="No tax charged in this period."
        text="The tax on the invoices and receipts you issue shows up here, at each rate."
        icon={FiPercent}
      />
    )}
  </Panel>
);

// The tax clients withheld from their payments in the period, per client and
// per month, with each payment behind a disclosure.
const TaxWithheld = ({
  report,
  entry,
  currency,
  elsewhere,
  periodFile,
  busy,
}) => {
  const largest = Number(entry?.clients[0]?.withheld) || 0;
  const count = entry?.payments.length || 0;
  return (
    <Panel
      title="Tax withheld"
      description="Tax your clients withheld from their payments in this period, by the date paid, and paid to the tax office for you. The CSV file lists every payment."
      aside={
        <CsvButton
          section="tds"
          report={report}
          name="tax withheld"
          fileName={`tax-withheld-${periodFile}`}
        />
      }
    >
      {elsewhere.length > 0 && (
        <p className={`mb-4 ${panelNote}`}>
          Tax withheld in {elsewhere.join(", ")} shows when the report is by
          currency.
        </p>
      )}
      {entry ? (
        <div
          className={`transition-opacity ${busy ? "opacity-60" : ""}`}
          aria-busy={busy || undefined}
        >
          <p className="mb-5 text-[14px] text-spot-body">
            <span className="text-spot-ink">
              {money(entry.withheld, currency)}
            </span>{" "}
            withheld from {count.toLocaleString("en")}{" "}
            {count === 1 ? "payment" : "payments"}.
          </p>
          <div className="grid min-w-0 gap-6 min-[1100px]:grid-cols-2">
            <DataTable
              label={`Tax withheld by client, ${currency}`}
              narrow
              rows={entry.clients}
              rowKey={(row) => row.customerId || `name-${row.name}`}
              columns={[
                {
                  label: "Client",
                  type: "main",
                  render: (row) => (
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
                      {row.pan && (
                        <span className={`block ${panelNote}`}>
                          PAN {row.pan}
                        </span>
                      )}
                      <ShareBar
                        ratio={share(row.withheld, largest)}
                        tone="bg-card-lilac-bar"
                      />
                    </>
                  ),
                },
                {
                  label: "Payments",
                  render: (row) => row.payments.toLocaleString("en"),
                },
                amountColumn("Received", "received", currency),
                amountColumn("Tax withheld", "withheld", currency),
              ]}
            />
            <DataTable
              label={`Tax withheld by month, ${currency}`}
              narrow
              rows={entry.months}
              rowKey={(row) => row.month}
              columns={[
                {
                  label: "Month",
                  type: "main",
                  render: (row) => monthYear(row.month),
                },
                {
                  label: "Payments",
                  render: (row) => row.payments.toLocaleString("en"),
                },
                amountColumn("Tax withheld", "withheld", currency),
              ]}
            />
          </div>
          <details className="group mt-4">
            <summary className={`w-max cursor-pointer ${textLink}`}>
              Show each payment
            </summary>
            <div className="mt-3">
              <DataTable
                label={`Payments tax was withheld from, ${currency}`}
                rows={entry.payments}
                rowKey={(row, index) => `${row.documentId}-${index}`}
                columns={[
                  {
                    label: "Date paid",
                    type: "main",
                    render: (row) => dateLabel(row.datePaid),
                  },
                  {
                    label: "Client",
                    render: (row) => (
                      <>
                        {row.client || "Unnamed client"}
                        {row.pan && (
                          <span className={`block ${panelNote}`}>
                            PAN {row.pan}
                          </span>
                        )}
                      </>
                    ),
                  },
                  {
                    label: "Document",
                    render: (row) => (
                      <Link
                        className={textLink}
                        to={`/document/${row.documentId}`}
                      >
                        {row.documentNumber || "Unnumbered"}
                      </Link>
                    ),
                  },
                  amountColumn("Received", "received", currency),
                  amountColumn("Tax withheld", "withheld", currency),
                ]}
              />
            </div>
          </details>
        </div>
      ) : (
        <EmptyState
          title="No tax withheld in this period."
          text="When a client withholds tax from a payment, add it to that payment and it shows up here."
          icon={FiPercent}
        />
      )}
    </Panel>
  );
};

const ReportsPage = () => {
  useTitle("Reports — Invoice Spot");
  const [params, setParams] = useSearchParams();
  const period = params.get("period") || DEFAULT_PERIOD;
  // Everything in the reporting currency, at each document's own rate.
  const view = params.get("view") === "base" ? "base" : undefined;
  const query = useGetReportQuery({ period, view });
  const tdsQuery = useGetTdsReportQuery(period);
  const taxQuery = useGetTaxReportQuery({ period, view });
  const profitQuery = useGetProfitReportQuery(period);
  const gstMode =
    useGetSettingsQuery().data?.settings?.taxRegime === "GST_INDIA";
  const loading =
    query.isLoading ||
    tdsQuery.isLoading ||
    taxQuery.isLoading ||
    profitQuery.isLoading;
  const report = query.data;
  // A link from the overview names a section, such as #ageing. Once the
  // report is drawn, the page opens there and keyboard focus starts there.
  // The sections' ids are plain words, so the hash is used as it is.
  const drawn = !loading && Boolean(report);
  const { hash } = useLocation();
  useEffect(() => {
    const section = drawn && hash && document.getElementById(hash.slice(1));
    if (!section) return;
    section.scrollIntoView();
    section.focus({ preventScroll: true });
  }, [drawn, hash]);
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
          view,
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
            {!view && currencies.length > 1 && (
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
            {report &&
              (view ||
                currencies.some(
                  (entry) => entry.currency !== report.baseCurrency,
                )) && (
                <label className={filterChip}>
                  <input
                    type="checkbox"
                    className="sr-only"
                    checked={Boolean(view)}
                    onChange={(event) =>
                      choose({ view: event.target.checked ? "base" : null })
                    }
                  />
                  Show in {report.baseCurrency}
                </label>
              )}
            <label className="flex items-center gap-2.5">
              <span className="sr-only">Period</span>
              <Select
                size="toolbar"
                value={period}
                onChange={(period) => choose({ period })}
              >
                {options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </label>
          </>
        }
      />
      {report?.view === "base" && report.notConverted.length > 0 && (
        <p
          className="rounded-2xl border border-spot-violet/15 bg-white/60 px-4 py-3 text-[13.5px] leading-[1.6] text-spot-body"
          role="note"
        >
          Left out, with no exchange rate to {report.baseCurrency}:{" "}
          {report.notConverted
            .map(
              (entry) =>
                `${entry.documents.toLocaleString("en")} ${entry.documents === 1 ? "document" : "documents"} ${entry.currency ? `in ${entry.currency}` : "with no currency"}, ${money(entry.amount, entry.currency)}`,
            )
            .join("; ")}
          . Add a rate to a draft before you issue it.
        </p>
      )}
      {loading ? (
        <LoadingState />
      ) : (query.isError && !query.isFetching) ||
        (tdsQuery.isError && !tdsQuery.isFetching) ||
        (taxQuery.isError && !taxQuery.isFetching) ||
        (profitQuery.isError && !profitQuery.isFetching) ? (
        <ErrorState
          error={
            query.error || tdsQuery.error || taxQuery.error || profitQuery.error
          }
          retry={() => {
            query.refetch();
            tdsQuery.refetch();
            taxQuery.refetch();
            profitQuery.refetch();
          }}
        />
      ) : !shown ? (
        <>
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
          {/* Profit and loss counts expenses too, so it can have figures when
              the rest of the report has none. */}
          {profitQuery.data?.currencies.length > 0 && (
            <ProfitAndLoss
              report={profitQuery.data}
              periodName={periodName}
              busy={profitQuery.isFetching}
            />
          )}
        </>
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
            id="income"
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
                        className={`size-3 rounded-[3px] ${series.column}`}
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
          <ProfitAndLoss
            report={profitQuery.data}
            periodName={periodName}
            busy={profitQuery.isFetching}
          />
          <div className="grid min-w-0 gap-6 compact:gap-5 min-[1100px]:grid-cols-2 max-[700px]:gap-5">
            <Panel
              id="ageing"
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
            id="clients"
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
          <TaxCharged
            report={report}
            entry={taxQuery.data?.currencies.find(
              (entry) => entry.currency === currency,
            )}
            currency={currency}
            periodFile={periodFile}
            busy={taxQuery.isFetching}
          />
          {gstMode && <GstHelpers />}
          <TaxWithheld
            report={report}
            entry={tdsQuery.data?.currencies.find(
              (entry) => entry.currency === currency,
            )}
            currency={currency}
            elsewhere={
              view
                ? (tdsQuery.data?.currencies || [])
                    .filter((entry) => entry.currency !== currency)
                    .map((entry) => entry.currency || "no currency")
                : []
            }
            periodFile={periodFile}
            busy={tdsQuery.isFetching}
          />
        </>
      )}
    </WorkspacePage>
  );
};

export default ReportsPage;
