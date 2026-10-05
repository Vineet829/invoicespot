import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  FiCreditCard,
  FiDownload,
  FiEdit2,
  FiPlus,
  FiSearch,
  FiTrash2,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import useFileDownload from "../../../hooks/useFileDownload";
import useLastAnswer from "../../../hooks/useLastAnswer";
import { PAGE_SIZES } from "../../../utils/listState";
import CurrencyFilter from "../../documents/components/CurrencyFilter";
import ResultLine from "../../documents/components/ResultLine";
import { PERIODS } from "../../documents/documentFilters";
import { useGetDocumentSummaryQuery } from "../../documents/documentsApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import {
  useDeleteExpenseMutation,
  useGetExpenseCategoriesQuery,
  useGetExpensesCsvMutation,
  useGetExpensesQuery,
} from "../expensesApiSlice";
import {
  expenseFilterQuery,
  expenseListSearch,
  expenseQuery,
  expenseResults,
  isFiltered,
  readExpenseList,
} from "../expenseModel";
import ExpenseDialog from "../components/ExpenseDialog";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  SearchField,
  ConfirmDialog,
  EmptyState,
  LoadingState,
  ErrorState,
  Amount,
  dateInput,
  dateLabel,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  rowAction,
  rowActionDanger,
  secondaryButton,
  secondaryIconButton,
} from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";
import { RefusalNote, useBusiness } from "../../business/permissions";

const NO_EXPENSES = [];
const NO_CATEGORIES = [];

const ExpensesPage = () => {
  useTitle("Expenses — Invoice Spot");
  const [list, update] = useListState(readExpenseList, expenseListSearch);
  const [downloadCsv, exporting] = useFileDownload(useGetExpensesCsvMutation);
  const search = useCallback((q) => update({ q, page: 1 }), [update]);
  const query = useGetExpensesQuery(expenseQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  // The currencies the business bills in, for the currency menu.
  const currencies = useGetDocumentSummaryQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  }).data?.currencies;
  // What the result line says comes from the last answer and the filters
  // it answered.
  const answered = useLastAnswer(query.currentData, list);
  const categories =
    useGetExpenseCategoriesQuery().data?.categories || NO_CATEGORIES;
  const rows = query.data?.expenses || NO_EXPENSES;
  const count = query.data?.totalExpenses ?? 0;
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

  const { business, reason } = useBusiness();
  const editRefusal = reason("EDIT_RECORDS");
  // `editing` is the expense being edited, or "new" for the add dialog.
  const [editing, setEditing] = useState(null);
  // A link to record an expense, such as the overview's, opens the add
  // dialog once with `new=1`, which then leaves the address in place, so a
  // reload or the back button doesn't open it again. It waits for the
  // business, since a role that can't add expenses gets the list instead,
  // and for the settings the form starts from, so the dialog opens on its
  // first field. The link that led here is gone, so once the dialog closes,
  // focus moves to New expense.
  const [params, setParams] = useSearchParams();
  const adding = params.get("new") === "1";
  const formReady = Boolean(useGetSettingsQuery().data?.settings);
  const newButton = useRef(null);
  const openedByLink = useRef(false);
  const [focusNew, setFocusNew] = useState(false);
  useEffect(() => {
    if (!adding || !business || !formReady) return;
    if (!editRefusal) {
      openedByLink.current = true;
      setEditing("new");
    }
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("new");
        return next;
      },
      { replace: true },
    );
  }, [adding, business, formReady, editRefusal, setParams]);
  const closeDialog = () => {
    setEditing(null);
    if (!openedByLink.current) return;
    openedByLink.current = false;
    setFocusNew(true);
  };
  useEffect(() => {
    if (!focusNew) return;
    newButton.current?.focus();
    setFocusNew(false);
  }, [focusNew]);
  const [removing, setRemoving] = useState(null);
  const [deleteExpense, { isLoading: deleting }] = useDeleteExpenseMutation();
  const remove = async () => {
    try {
      const result = await deleteExpense(removing._id).unwrap();
      toast.success(result.message || "Expense deleted.");
      setRemoving(null);
    } catch (error) {
      toast.error(errorMessage(error, "The expense could not be deleted."));
    }
  };
  const filtered = isFiltered(list);
  // Clear filters is gone once they are cleared, so focus moves to the
  // search box, as soon as the tools are shown.
  const tools = useRef(null);
  const [refocus, setRefocus] = useState(false);
  const clearFilters = () => {
    update({ q: "", category: "", period: "", currency: "", page: 1 });
    setRefocus(true);
  };
  // A new business has nothing to search or filter yet.
  const showTools = Boolean(query.data) && (count > 0 || filtered);
  useEffect(() => {
    const box = refocus && tools.current?.querySelector('input[type="search"]');
    if (!box) return;
    box.focus();
    setRefocus(false);
  }, [refocus, showTools]);
  // A category from an old link stays chosen after its last expense is gone.
  const categoryOptions =
    list.category && !categories.includes(list.category)
      ? [...categories, list.category]
      : categories;

  const columns = [
    {
      label: "No.",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Vendor",
      type: "main",
      render: (row) =>
        row.vendor || <span className="font-normal text-spot-faint">—</span>,
    },
    { label: "Category", render: (row) => row.category },
    { label: "Date paid", render: (row) => dateLabel(row.date) },
    {
      label: "Tax",
      type: "amount",
      // Tax claimed back is not part of the cost.
      render: (row) =>
        Number(row.taxAmount) > 0 ? (
          <span className="inline-flex flex-col items-end gap-0.5 stacked:items-start">
            <Amount value={row.taxAmount} currency={row.currency} hideCode />
            {row.taxReclaimable && (
              <span className="text-[12px] text-spot-muted">Reclaimable</span>
            )}
          </span>
        ) : (
          <span className="text-spot-faint">—</span>
        ),
    },
    {
      label: "Total",
      type: "amount",
      render: (row) => <Amount value={row.total} currency={row.currency} />,
    },
    {
      label: "Edit",
      type: "action",
      render: (row) => (
        <button
          type="button"
          className={rowAction}
          onClick={() => setEditing(row)}
          aria-label={`Edit the expense of ${money(row.total, row.currency)} on ${dateLabel(row.date)}`}
        >
          <FiEdit2 aria-hidden="true" />
          <span>Edit</span>
        </button>
      ),
    },
    {
      label: "Delete",
      type: "action",
      render: (row) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() => setRemoving(row)}
          aria-label={`Delete the expense of ${money(row.total, row.currency)} on ${dateLabel(row.date)}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  // Viewers see the expenses, but not the ways to change them.
  const shown = editRefusal
    ? columns.filter((column) => !["Edit", "Delete"].includes(column.label))
    : columns;

  const results = query.isError ? (
    <ErrorState error={query.error} retry={query.refetch} />
  ) : rows.length ? (
    <>
      {filtered && isFiltered(answered.list) && answered.data && (
        <ResultLine
          parts={expenseResults(answered.data, currencies)}
          onClear={clearFilters}
          busy={query.isFetching}
        />
      )}
      <DataTable
        columns={shown}
        rows={pagination.rows}
        fillRows={pagination.fullPage}
        label="Expenses"
        busy={query.isFetching}
      />
      <Pagination pagination={pagination} sizes={PAGE_SIZES} />
    </>
  ) : filtered ? (
    <EmptyState
      title="No expenses match."
      text="Try another search or filter, or clear them to see every expense."
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
      title="Know what your work costs."
      text="Record what you spend, with the tax you paid on it."
      icon={FiCreditCard}
    />
  );

  return (
    <WorkspacePage section="Expenses" fill>
      <PageHeader
        title="Expenses"
        description="What your business spends, by category, with the tax paid on it."
        actions={
          <>
            <button
              type="button"
              className={`${secondaryIconButton} max-[700px]:!flex-none`}
              onClick={() =>
                downloadCsv(
                  expenseFilterQuery(list),
                  `invoice-spot-expenses-${dateInput()}.csv`,
                )
              }
              disabled={exporting}
              aria-label="Download these expenses as CSV"
              title="Download these expenses as CSV"
            >
              <FiDownload aria-hidden="true" />
            </button>
            <button
              ref={newButton}
              type="button"
              className={primaryButton}
              onClick={() => setEditing("new")}
              disabled={Boolean(editRefusal)}
              title={editRefusal}
              aria-describedby={
                editRefusal ? "expense-edit-refusal" : undefined
              }
            >
              <FiPlus aria-hidden="true" /> New expense
            </button>
          </>
        }
      />
      <RefusalNote id="expense-edit-refusal" reason={editRefusal} />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel
          title="Your expenses"
          fill={rows.length > 0 && !query.isError}
          aside={
            showTools && (
              <div
                ref={tools}
                className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full"
              >
                <SearchField
                  value={list.q}
                  onSearch={search}
                  label="Search expenses"
                  placeholder="Vendor, category or note"
                  className="w-[240px] max-[1439px]:w-[196px] max-[700px]:w-full"
                />
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Category</span>
                  <Select
                    size="toolbar"
                    className="max-[700px]:w-full"
                    value={list.category}
                    onChange={(category) => update({ category, page: 1 })}
                  >
                    <option value="">All categories</option>
                    {categoryOptions.map((category) => (
                      <option key={category} value={category}>
                        {category}
                      </option>
                    ))}
                  </Select>
                </label>
                <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                  <span className="sr-only">Paid</span>
                  <Select
                    size="toolbar"
                    className="max-[700px]:w-full"
                    value={list.period}
                    onChange={(period) => update({ period, page: 1 })}
                  >
                    {PERIODS.map((period) => (
                      <option key={period.value} value={period.value}>
                        {period.label}
                      </option>
                    ))}
                  </Select>
                </label>
                <CurrencyFilter
                  value={list.currency}
                  currencies={currencies}
                  onChange={(currency) => update({ currency, page: 1 })}
                />
              </div>
            )
          }
        >
          {results}
        </Panel>
      )}
      <ExpenseDialog
        open={Boolean(editing)}
        expense={editing === "new" ? null : editing}
        onClose={closeDialog}
      />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this expense?"
        confirmLabel="Delete expense"
        onConfirm={remove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        The expense of {removing && money(removing.total, removing.currency)}{" "}
        {removing?.vendor ? `to ${removing.vendor} ` : ""}on{" "}
        {removing && dateLabel(removing.date)} will be permanently deleted.
      </ConfirmDialog>
    </WorkspacePage>
  );
};

export default ExpensesPage;
