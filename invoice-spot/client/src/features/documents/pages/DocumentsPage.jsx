import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  FiDownload,
  FiEye,
  FiFileText,
  FiPlus,
  FiSearch,
  FiTrash2,
  FiX,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import useFileDownload from "../../../hooks/useFileDownload";
import useLastAnswer from "../../../hooks/useLastAnswer";
import { PAGE_SIZES, nextSort } from "../../../utils/listState";
import DocumentsTabs from "../components/DocumentsTabs";
import CurrencyFilter from "../components/CurrencyFilter";
import MoneyBar from "../components/MoneyBar";
import { opensList } from "../listLinks";
import ResultLine from "../components/ResultLine";
import WaitingPayments from "../components/WaitingPayments";
import { DocumentStatusBadge, documentName, isDraft } from "../documentState";
import { RefusalNote, useBusiness } from "../../business/permissions";
import {
  useDeleteDocMutation,
  useGetAllMyDocsQuery,
  useGetDocumentSummaryQuery,
  useGetDocumentsCsvMutation,
} from "../documentsApiSlice";
import {
  DUE_FILTERS,
  SORT_FIELDS,
  SORT_OPTIONS,
  STATUS_FILTERS,
  TYPE_FILTERS,
  PERIODS,
  documentFilterQuery,
  documentQuery,
  documentResults,
  isFiltered,
  listSearch,
  readListState,
} from "../documentFilters";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  SearchField,
  ConfirmDialog,
  EmptyState,
  LoadingState,
  ErrorState,
  Badge,
  Amount,
  DueDate,
  errorMessage,
  dateInput,
} from "../../../components/workspace/Workspace";
import {
  filterChip,
  rowAction,
  rowActionDanger,
  secondaryButton,
  secondaryIconButton,
  toolbarSelect,
} from "../../../components/workspace/pastel";

const NO_DOCUMENTS = [];

// A filter a link set, with a button that removes it. Its focus ring is
// white, to show on the dark chip.
const removableChip =
  "inline-flex h-9 items-center gap-1 whitespace-nowrap rounded-full border border-spot-night bg-spot-night pl-3.5 pr-1 text-[13px] text-white coarse:h-11";
const removeButton =
  "grid size-7 place-items-center rounded-full bg-transparent p-0 text-white/80 transition-colors hover:bg-white/15 hover:text-white focus-visible:outline-white focus-visible:outline-offset-0 coarse:size-9 [&>svg]:size-3.5 [&>svg]:stroke-[2]";

const DocumentsPage = () => {
  useTitle("Documents — Invoice Spot");
  const [list, update] = useListState(readListState, listSearch);
  const [downloadCsv, exporting] = useFileDownload(useGetDocumentsCsvMutation);
  const search = useCallback((q) => update({ q, page: 1 }), [update]);
  const query = useGetAllMyDocsQuery(documentQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const summaryQuery = useGetDocumentSummaryQuery(list.currency || undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const summary = summaryQuery.data;
  // What the result line says comes from the last answer and the filters
  // it answered.
  const answered = useLastAnswer(query.currentData, list);
  const rows = query.data?.myDocuments || NO_DOCUMENTS;
  const count = query.data?.totalDocuments ?? 0;
  const pagination = serverPagination({
    rows,
    count,
    page: list.page - 1,
    size: list.size,
    setPage: (page) => update({ page: page + 1 }),
    setSize: (size) => update({ size, page: 1 }),
  });
  // A page past the end, after a delete or from an old link, shows the last.
  const pages = query.currentData?.numberOfPages;
  useEffect(() => {
    if (pages && list.page > pages) update({ page: pages });
  }, [pages, list.page, update]);

  const { reason } = useBusiness();
  const deleteRefusal = reason("DELETE_RECORDS");
  const [selected, setSelected] = useState(null);
  const [deleteDoc, { isLoading: deleting }] = useDeleteDocMutation();
  const remove = async () => {
    try {
      const result = await deleteDoc(selected._id).unwrap();
      toast.success(result.message || "Document deleted.");
      setSelected(null);
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be deleted."));
    }
  };
  const filtered = isFiltered(list);
  // A filter's control is gone once the filter is removed, so focus moves
  // to the status chosen, as soon as the chips are shown.
  const statusGroup = useRef(null);
  const [refocus, setRefocus] = useState(false);
  const clearFilters = () => {
    update({
      q: "",
      status: "",
      due: "",
      type: "",
      period: "",
      currency: "",
      page: 1,
    });
    setRefocus(true);
  };
  const removeDue = () => {
    update({ due: "", page: 1 });
    setRefocus(true);
  };
  const dueFilter = DUE_FILTERS.find((filter) => filter.value === list.due);
  // A new account has nothing to search or filter yet.
  const showTools = Boolean(query.data) && (count > 0 || filtered);
  useEffect(() => {
    const checked =
      refocus && statusGroup.current?.querySelector("input:checked");
    if (!checked) return;
    checked.focus();
    setRefocus(false);
  }, [refocus, list.status, showTools]);

  const columns = [
    {
      label: "#",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Document no.",
      type: "main",
      sortBy: "number",
      render: (row) =>
        row.documentNumber || <span className="text-spot-muted">Draft</span>,
    },
    { label: "Type", render: (row) => <Badge>{row.documentType}</Badge> },
    {
      label: "Customer",
      sortBy: "client",
      render: (row) => row.customer?.name || "—",
    },
    {
      label: "Amount",
      type: "amount",
      sortBy: "total",
      render: (row) => <Amount value={row.total} currency={row.currency} />,
    },
    // What is left to pay on an issued invoice; nothing is owed on the rest.
    {
      label: "Balance due",
      type: "amount",
      render: (row) =>
        row.balanceDue == null ? (
          <span className="text-[13px] text-spot-muted">—</span>
        ) : (
          <Amount value={row.balanceDue} currency={row.currency} />
        ),
    },
    {
      label: "Due date",
      sortBy: "dueDate",
      render: (row) => <DueDate value={row.dueDate} overdue={row.overdue} />,
    },
    {
      label: "Status",
      render: (row) => <DocumentStatusBadge document={row} />,
    },
    {
      label: "View",
      type: "action",
      render: (row) => (
        <Link
          className={rowAction}
          to={`/document/${row._id}`}
          aria-label={`View ${documentName(row)}`}
        >
          <FiEye aria-hidden="true" />
          <span>View</span>
        </Link>
      ),
    },
    {
      label: "Delete",
      type: "action",
      // Only a draft is deleted; an issued document is voided from its page.
      render: (row) =>
        isDraft(row) && (
          <button
            type="button"
            className={rowActionDanger}
            onClick={() => setSelected(row)}
            aria-label={`Delete ${documentName(row)}`}
            disabled={Boolean(deleteRefusal)}
            title={deleteRefusal}
            aria-describedby={
              deleteRefusal ? "document-delete-refusal" : undefined
            }
          >
            <FiTrash2 aria-hidden="true" />
            <span>Delete</span>
          </button>
        ),
    },
  ];

  const results = query.isError ? (
    <ErrorState error={query.error} retry={query.refetch} />
  ) : rows.length ? (
    <>
      {filtered && isFiltered(answered.list) && answered.data && (
        <ResultLine
          parts={documentResults(
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
        fillRows={pagination.fullPage}
        label="Documents"
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
      title="No documents match."
      text="Try another search or filter, or clear them to see every document."
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
      title="Your work, ready to invoice."
      text="Create a document to keep your invoices, quotations, and receipts together."
      icon={FiFileText}
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
            <button
              type="button"
              className={`${secondaryIconButton} max-[700px]:!flex-none`}
              onClick={() =>
                downloadCsv(
                  documentFilterQuery(list),
                  `invoice-spot-documents-${dateInput()}.csv`,
                )
              }
              disabled={exporting}
              aria-label="Download these documents as CSV"
              title="Download these documents as CSV"
            >
              <FiDownload aria-hidden="true" />
            </button>
            <ActionLink to="/create-doc" reason={reason("EDIT_RECORDS")}>
              <FiPlus aria-hidden="true" /> Create document
            </ActionLink>
          </>
        }
      />
      <MoneyBar
        query={summaryQuery}
        shows={(link) => opensList(link, "/documents", readListState, list)}
      />
      <RefusalNote id="document-delete-refusal" reason={deleteRefusal} />
      <WaitingPayments />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel
          title={showTools ? undefined : "Your documents"}
          fill={rows.length > 0 && !query.isError}
        >
          {showTools && (
            // The tools take the title's row, so the rows keep their room.
            <div className="mb-5 flex flex-wrap items-center justify-between gap-x-4 gap-y-3 compact:mb-4">
              <h2 className="sr-only">Your documents</h2>
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <fieldset
                  ref={statusGroup}
                  className="m-0 flex min-w-0 flex-wrap gap-2 border-0 p-0"
                >
                  <legend className="sr-only">Status</legend>
                  {STATUS_FILTERS.map((filter) => (
                    <label key={filter.value} className={filterChip}>
                      {/* A week of due dates belongs to Outstanding. */}
                      <input
                        type="radio"
                        name="document-status"
                        className="sr-only"
                        checked={list.status === filter.value}
                        onChange={() =>
                          update({
                            status: filter.value,
                            ...(filter.value !== "outstanding" && { due: "" }),
                            page: 1,
                          })
                        }
                      />
                      {filter.label}
                    </label>
                  ))}
                </fieldset>
                {dueFilter && (
                  <span className={removableChip}>
                    {dueFilter.label}
                    <button
                      type="button"
                      className={removeButton}
                      onClick={removeDue}
                      aria-label={`Remove ${dueFilter.label}`}
                    >
                      <FiX aria-hidden="true" />
                    </button>
                  </span>
                )}
              </div>
              <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
                <SearchField
                  value={list.q}
                  onSearch={search}
                  label="Search documents"
                  placeholder="Number or customer"
                  className="w-[240px] max-[1439px]:w-[196px] max-[700px]:w-full"
                />
                <label className="min-w-0 max-[700px]:flex-[1_1_100%]">
                  <span className="sr-only">Document type</span>
                  {/* One option is much longer than the rest, so where the
                      browser can, the menu is as wide as the type chosen,
                      and on a phone it has a row of its own. */}
                  <select
                    className={`${toolbarSelect} [field-sizing:content] max-[700px]:w-full`}
                    value={list.type}
                    onChange={(event) =>
                      update({ type: event.target.value, page: 1 })
                    }
                  >
                    {TYPE_FILTERS.map((filter) => (
                      <option key={filter.value} value={filter.value}>
                        {filter.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Issued</span>
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
                  <span className="sr-only">Sort documents</span>
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
      <ConfirmDialog
        open={Boolean(selected)}
        title="Delete this document?"
        confirmLabel="Delete document"
        onConfirm={remove}
        onClose={() => setSelected(null)}
        busy={deleting}
      >
        {selected && documentName(selected)} will be permanently deleted.
      </ConfirmDialog>
    </WorkspacePage>
  );
};
export default DocumentsPage;
