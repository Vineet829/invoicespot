import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FiArrowDownLeft, FiEye, FiPlus, FiSearch } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import useLastAnswer from "../../../hooks/useLastAnswer";
import { PAGE_SIZES, nextSort } from "../../../utils/listState";
import DocumentsTabs from "../../documents/components/DocumentsTabs";
import CurrencyFilter from "../../documents/components/CurrencyFilter";
import MoneyBar from "../../documents/components/MoneyBar";
import { opensList } from "../../documents/listLinks";
import ResultLine from "../../documents/components/ResultLine";
import { PERIODS } from "../../documents/documentFilters";
import { useGetDocumentSummaryQuery } from "../../documents/documentsApiSlice";
import { useBusiness } from "../../business/permissions";
import { useGetPaymentsQuery } from "../paymentsApiSlice";
import {
  SORT_FIELDS,
  SORT_OPTIONS,
  isPaymentListFiltered,
  methodLabel,
  methodOptions,
  paymentListSearch,
  paymentQuery,
  paymentResults,
  readPaymentList,
} from "../paymentFilters";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  SearchField,
  EmptyState,
  LoadingState,
  ErrorState,
  Badge,
  Amount,
  dateLabel,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  rowAction,
  secondaryButton,
  textLink,
  toolbarSelect,
} from "../../../components/workspace/pastel";

const NO_PAYMENTS = [];
const DESCRIPTION =
  "Every payment recorded on an invoice, and every receipt. Refunds show as minus amounts.";

// Money in and out on issued documents, one row each: payments on invoices,
// receipts, and refunds as minus amounts. A view of the Documents page, so
// it has the same header and money bar.
const PaymentsPage = () => {
  useTitle("Payments — Invoice Spot");
  const [list, update] = useListState(readPaymentList, paymentListSearch);
  const search = useCallback((q) => update({ q, page: 1 }), [update]);
  const query = useGetPaymentsQuery(paymentQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const filtered = isPaymentListFiltered(list);
  const summaryQuery = useGetDocumentSummaryQuery(list.currency || undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const summary = summaryQuery.data;
  // What the result line says comes from the last answer and the filters
  // it answered.
  const answered = useLastAnswer(query.currentData, list);
  const rows = query.data?.payments || NO_PAYMENTS;
  const count = query.data?.total ?? 0;
  const pagination = serverPagination({
    rows,
    count,
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
  // Clear filters is gone once they are cleared, so focus moves to the
  // search box, as soon as the tools are shown.
  const tools = useRef(null);
  const [refocus, setRefocus] = useState(false);
  const clearFilters = () => {
    update({ q: "", method: "", period: "", currency: "", page: 1 });
    setRefocus(true);
  };
  // A new account has nothing to search or filter yet.
  const showTools = Boolean(query.data) && (count > 0 || filtered);
  useEffect(() => {
    const box = refocus && tools.current?.querySelector('input[type="search"]');
    if (!box) return;
    box.focus();
    setRefocus(false);
  }, [refocus, showTools]);

  const columns = [
    {
      label: "#",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Date paid",
      sortBy: "date",
      render: (row) => dateLabel(row.date),
    },
    {
      label: "Customer",
      type: "main",
      render: (row) => row.client || "—",
    },
    {
      label: "Document",
      render: (row) => (
        <Link className={textLink} to={`/document/${row.document.id}`}>
          {row.document.number || "Unnumbered"}
        </Link>
      ),
    },
    { label: "Method", render: (row) => <Badge>{methodLabel(row)}</Badge> },
    { label: "Note", render: (row) => row.note || "—" },
    {
      label: "Amount",
      type: "amount",
      sortBy: "amount",
      render: (row) => <Amount value={row.amount} currency={row.currency} />,
    },
    {
      label: "View",
      type: "action",
      render: (row) => (
        <Link
          className={rowAction}
          to={`/document/${row.document.id}`}
          aria-label={`View ${row.document.number || "the document"}`}
        >
          <FiEye aria-hidden="true" />
          <span>View</span>
        </Link>
      ),
    },
  ];

  const results = query.isError ? (
    <ErrorState error={query.error} retry={query.refetch} />
  ) : rows.length ? (
    <>
      {filtered && isPaymentListFiltered(answered.list) && answered.data && (
        <ResultLine
          parts={paymentResults(
            answered.list,
            answered.data,
            summary?.currencies,
          )}
          onClear={clearFilters}
          busy={query.isFetching}
        />
      )}
      <DataTable
        columns={columns}
        rows={pagination.rows}
        rowKey={(row) => `${row.kind}-${row.id}`}
        fillRows={pagination.fullPage}
        label="Payments"
        sort={{ field: list.sort, dir: list.dir }}
        onSort={(field) =>
          update({ ...nextSort(SORT_FIELDS, list, field), page: 1 })
        }
        busy={query.isFetching}
      />
      <Pagination pagination={pagination} sizes={PAGE_SIZES} />
    </>
  ) : filtered ? (
    <EmptyState
      title="No payments match."
      text="Try another search or filter, or clear them to see every payment."
      icon={FiSearch}
      action={
        <button
          type="button"
          className={secondaryButton}
          onClick={clearFilters}
        >
          Clear filters
        </button>
      }
    />
  ) : (
    <EmptyState
      title="No payments yet."
      text="Payments you record on invoices, and the receipts you issue, show up here."
      icon={FiArrowDownLeft}
    />
  );

  return (
    <WorkspacePage section="Documents" fill>
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
      <MoneyBar
        query={summaryQuery}
        shows={(link) => opensList(link, "/payments", readPaymentList, list)}
      />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel
          title={showTools ? undefined : "Payments"}
          description={showTools ? undefined : DESCRIPTION}
          fill={rows.length > 0 && !query.isError}
        >
          {showTools && (
            <div ref={tools} className="mb-5 flex flex-col gap-3 compact:mb-4">
              <h2 className="sr-only">Payments</h2>
              <p className={panelNote}>{DESCRIPTION}</p>
              <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
                <SearchField
                  value={list.q}
                  onSearch={search}
                  label="Search payments"
                  placeholder="Customer or number"
                  className="w-[240px] max-[1439px]:w-[196px] max-[700px]:w-full"
                />
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Payment method</span>
                  <select
                    className={`${toolbarSelect} max-[700px]:w-full`}
                    value={list.method}
                    onChange={(event) =>
                      update({ method: event.target.value, page: 1 })
                    }
                  >
                    {methodOptions(query.data?.methods, list.method).map(
                      (option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ),
                    )}
                  </select>
                </label>
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Date paid</span>
                  <select
                    className={`${toolbarSelect} max-[700px]:w-full`}
                    value={list.period}
                    onChange={(event) =>
                      update({ period: event.target.value, page: 1 })
                    }
                  >
                    {PERIODS.map((period) => (
                      <option key={period.value} value={period.value}>
                        {period.label}
                      </option>
                    ))}
                  </select>
                </label>
                <CurrencyFilter
                  value={list.currency}
                  currencies={summary?.currencies}
                  onChange={(currency) => update({ currency, page: 1 })}
                />
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Sort payments</span>
                  <select
                    className={`${toolbarSelect} max-[700px]:w-full`}
                    value={`${list.sort}:${list.dir}`}
                    onChange={(event) => {
                      const [sort, dir] = event.target.value.split(":");
                      update({ sort, dir, page: 1 });
                    }}
                  >
                    {SORT_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
          )}
          {results}
        </Panel>
      )}
    </WorkspacePage>
  );
};

export default PaymentsPage;
