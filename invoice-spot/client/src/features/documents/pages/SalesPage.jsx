import { useEffect } from "react";
import { Link } from "react-router-dom";
import { FiFileText, FiPlus } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import { PAGE_SIZES } from "../../../utils/listState";
import { countOf } from "../../../utils/counts";
import { dateSpan } from "../../../utils/dates";
import { formatAmount } from "../../../utils/money";
import DocumentsTabs from "../components/DocumentsTabs";
import CurrencyChoice from "../components/CurrencyChoice";
import PeriodFilter from "../components/PeriodFilter";
import SummaryFigure from "../components/SummaryFigure";
import { useGetAllMyDocsQuery, useGetSalesQuery } from "../documentsApiSlice";
import {
  TYPE_NAMES,
  billedQuery,
  readSales,
  salesQuery,
  salesSearch,
} from "../salesModel";
import { share } from "../../reports/reportModel";
import { useBusiness } from "../../business/permissions";
import useBusinessToday from "../../business/useBusinessToday";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  EmptyState,
  LoadingState,
  ErrorState,
  Amount,
  Badge,
  ShareBar,
  dateLabel,
  money,
} from "../../../components/workspace/Workspace";
import {
  panel,
  panelNote,
  skeleton,
  textLink,
} from "../../../components/workspace/pastel";

const NO_DOCUMENTS = [];
const NO_ROWS = [];

const partTitle = "mb-1 text-[14px] font-medium text-spot-ink";

// While the figures load, blocks keep the summary's room, so the list below
// stays where it is when they arrive.
const SummaryPlaceholder = () => (
  <div className={panel} aria-hidden="true">
    <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
      <div className="flex flex-col gap-2">
        <span className={`${skeleton} h-[13px] w-[60px] rounded-md`} />
        <span className={`${skeleton} h-9 w-[260px] rounded-md`} />
        <span className={`${skeleton} h-[22px] w-[220px] rounded-full`} />
      </div>
      <span className={`${skeleton} h-11 w-[180px] rounded-xl`} />
    </div>
    <div className="mt-6 grid min-w-0 gap-x-10 gap-y-6 compact:mt-5 min-[1280px]:grid-cols-2">
      {[3, 2].map((rows) => (
        <div className="flex min-w-0 flex-col gap-3" key={rows}>
          <span className={`${skeleton} h-3.5 w-[100px] rounded-md`} />
          {Array.from({ length: rows }, (_, index) => (
            <span className={`${skeleton} h-8 w-full rounded-md`} key={index} />
          ))}
        </div>
      ))}
    </div>
  </div>
);

// What was billed in the period, in one currency: the total against the
// period before when there is one, then by type, credit notes taking off,
// then the tax in it by rate. The server lists every type, those with
// nothing billed too, so the types give way to a note only when none has
// any. The period and currency menus sit beside the total, and the list
// below follows them.
const Summary = ({ sales, period, onPeriod, onCurrency, busy }) => {
  const { currency } = sales;
  const several = sales.currencies?.length > 1;
  const types = sales.byType || NO_ROWS;
  const rates = sales.tax?.rates || NO_ROWS;
  const largest = Math.max(0, ...types.map((row) => Number(row.amount)));
  const days =
    sales.period?.from && sales.period?.to
      ? `, issued ${dateSpan(sales.period.from, sales.period.to)}`
      : "";
  const amount = (value) => (
    <Amount value={value} currency={currency} hideCode={!several} />
  );
  return (
    <Panel>
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <h2 className="sr-only">Sales</h2>
        <SummaryFigure
          label="Billed"
          total={sales.total}
          currency={currency}
          several={several}
          detail={`on ${countOf(sales.documents, "document", "documents")}${days}`}
          period={sales.period}
          previousTotal={sales.previousTotal}
        />
        <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
          <PeriodFilter label="Issued" value={period} onChange={onPeriod} />
          <CurrencyChoice
            value={currency}
            currencies={sales.currencies}
            onChange={onCurrency}
          />
        </div>
      </div>
      <div
        className={`mt-6 grid min-w-0 gap-x-10 gap-y-6 transition-opacity compact:mt-5 min-[1280px]:grid-cols-2 ${busy ? "opacity-60" : ""}`}
        aria-busy={busy || undefined}
      >
        <div className="min-w-0">
          <h3 className={partTitle}>By type</h3>
          {types.some((row) => Number(row.documents) > 0) ? (
            <DataTable
              label={`Billed by type, ${currency}`}
              narrow
              rows={types}
              rowKey={(row) => row.type}
              columns={[
                {
                  label: "Type",
                  type: "main",
                  render: (row) => (
                    <>
                      {TYPE_NAMES[row.type] || row.type}
                      <ShareBar
                        ratio={share(row.amount, largest)}
                        tone="bg-card-blue-bar"
                      />
                    </>
                  ),
                },
                {
                  label: "Documents",
                  type: "number",
                  render: (row) => Number(row.documents).toLocaleString("en"),
                },
                {
                  label: "Amount",
                  type: "amount",
                  render: (row) => amount(row.amount),
                },
              ]}
            />
          ) : (
            <p className={`mt-2 ${panelNote}`}>Nothing was billed.</p>
          )}
        </div>
        <div className="min-w-0">
          <h3 className={partTitle}>Tax by rate</h3>
          {rates.length ? (
            <>
              <DataTable
                label={`Tax by rate, ${currency}`}
                narrow
                rows={rates}
                rowKey={(row) => row.rate}
                columns={[
                  {
                    label: "Rate",
                    type: "main",
                    render: (row) => `${row.rate}%`,
                  },
                  {
                    label: "Documents",
                    type: "number",
                    render: (row) => Number(row.documents).toLocaleString("en"),
                  },
                  {
                    label: "Taxable",
                    type: "amount",
                    render: (row) => amount(row.taxable),
                  },
                  {
                    label: "Tax",
                    type: "amount",
                    render: (row) => amount(row.tax),
                  },
                ]}
              />
              <p className={`mt-3 ${panelNote}`}>
                The total includes{" "}
                {several
                  ? money(sales.tax.total, currency)
                  : formatAmount(sales.tax.total, currency)}{" "}
                tax.
              </p>
            </>
          ) : (
            <p className={`mt-2 ${panelNote}`}>No tax was charged.</p>
          )}
        </div>
      </div>
    </Panel>
  );
};

// Documents › Sales: what was billed in a period, as the overview's Billed
// figure counts it, explained by type and tax rate, above the documents
// behind it, newest first. The period, the currency and the page are kept
// in the address.
const SalesPage = () => {
  useTitle("Sales — Invoice Spot");
  const [list, update] = useListState(readSales, salesSearch);
  const summaryQuery = useGetSalesQuery(salesQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const sales = summaryQuery.data;
  // The documents are in the total's currency: the one the address names,
  // or else the one the server answered in.
  const currency = list.currency || sales?.currency;
  // The calendar's periods are days of the business's calendar, as the
  // summary's are.
  const today = useBusinessToday();
  const query = useGetAllMyDocsQuery(billedQuery(list, currency, today), {
    skip: !list.currency && !sales,
    refetchOnMountOrArgChange: 60,
  });
  const rows = query.data?.myDocuments || NO_DOCUMENTS;
  const pagination = serverPagination({
    rows,
    count: query.data?.totalDocuments ?? 0,
    page: list.page - 1,
    size: list.size,
    setPage: (page) => update({ page: page + 1 }),
    setSize: (size) => update({ size, page: 1 }),
  });
  // A page past the end, from an old link, shows the last.
  const pages = query.currentData?.numberOfPages;
  useEffect(() => {
    if (pages && list.page > pages) update({ page: pages });
  }, [pages, list.page, update]);
  const { reason } = useBusiness();

  const columns = [
    {
      label: "Document",
      type: "main",
      render: (row) => (
        <Link className={textLink} to={`/document/${row._id}`}>
          {row.documentNumber || "No number yet"}
        </Link>
      ),
    },
    {
      label: "Type",
      render: (row) => <Badge>{row.documentType}</Badge>,
    },
    { label: "Client", render: (row) => row.customer?.name || "—" },
    { label: "Issued", render: (row) => dateLabel(row.issueDate) },
    {
      label: "Amount",
      type: "amount",
      render: (row) => <Amount value={row.total} currency={row.currency} />,
    },
  ];

  return (
    <WorkspacePage section="Documents">
      <PageHeader
        title="Documents"
        description="Invoices, quotations, and receipts. All your paperwork in one place."
        actions={
          <>
            <DocumentsTabs />
            <ActionLink to="/create-doc" reason={reason("EDIT_RECORDS")}>
              <FiPlus aria-hidden="true" /> Create document
            </ActionLink>
          </>
        }
      />
      {summaryQuery.isError && !sales ? (
        <ErrorState error={summaryQuery.error} retry={summaryQuery.refetch} />
      ) : (
        <>
          {sales ? (
            <Summary
              sales={sales}
              period={list.period}
              onPeriod={(period) => update({ period, page: 1 })}
              onCurrency={(chosen) => update({ currency: chosen, page: 1 })}
              busy={summaryQuery.isFetching}
            />
          ) : (
            <SummaryPlaceholder />
          )}
          {query.isLoading || query.isUninitialized ? (
            <LoadingState />
          ) : (
            <Panel
              title="Billed documents"
              description="The invoices, receipts and credit notes behind the total, newest first."
            >
              {query.isError ? (
                <ErrorState error={query.error} retry={query.refetch} />
              ) : rows.length ? (
                <>
                  <DataTable
                    columns={columns}
                    rows={pagination.rows}
                    label="Billed documents"
                    busy={query.isFetching}
                  />
                  <Pagination pagination={pagination} sizes={PAGE_SIZES} />
                </>
              ) : (
                <EmptyState
                  title={
                    list.period
                      ? "Nothing was billed in this period."
                      : "Nothing billed yet."
                  }
                  text="Invoices, receipts and credit notes show up here once they are issued, newest first."
                  icon={FiFileText}
                />
              )}
            </Panel>
          )}
        </>
      )}
    </WorkspacePage>
  );
};

export default SalesPage;
