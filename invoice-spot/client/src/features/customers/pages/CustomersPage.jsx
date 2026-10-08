import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  FiDownload,
  FiEye,
  FiPlus,
  FiSearch,
  FiTrash2,
  FiUpload,
  FiUsers,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import useFileDownload from "../../../hooks/useFileDownload";
import { PAGE_SIZES, nextSort } from "../../../utils/listState";
import {
  useDeleteCustomerMutation,
  useGetAllUserCustomersQuery,
  useGetCustomersCsvMutation,
} from "../customersApiSlice";
import {
  SORT_FIELDS,
  SORT_OPTIONS,
  clientFilterQuery,
  clientListSearch,
  clientQuery,
  readClientList,
} from "../clientFilters";
import ImportClientsDialog from "../components/ImportClientsDialog";
import ClientsTabs from "../components/ClientsTabs";
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
  Amount,
  errorMessage,
  dateInput,
} from "../../../components/workspace/Workspace";
import {
  rowAction,
  rowActionDanger,
  secondaryButton,
  secondaryIconButton,
} from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";
import { RefusalNote, useBusiness } from "../../business/permissions";

const NO_CLIENTS = [];

const CustomersPage = () => {
  useTitle("Clients — Invoice Spot");
  const [list, update] = useListState(readClientList, clientListSearch);
  const [downloadCsv, exporting] = useFileDownload(useGetCustomersCsvMutation);
  const [importing, setImporting] = useState(false);
  const search = useCallback((q) => update({ q, page: 1 }), [update]);
  const query = useGetAllUserCustomersQuery(clientQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const rows = query.data?.myCustomers || NO_CLIENTS;
  const count = query.data?.totalCustomers ?? 0;
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
  const editRefusal = reason("EDIT_RECORDS");
  const deleteRefusal = reason("DELETE_RECORDS");
  const [selected, setSelected] = useState(null);
  const [deleteCustomer, { isLoading: deleting }] = useDeleteCustomerMutation();
  const remove = async () => {
    try {
      await deleteCustomer(selected._id).unwrap();
      toast.success("Customer deleted successfully.");
      setSelected(null);
    } catch (error) {
      toast.error(errorMessage(error, "The customer could not be deleted."));
    }
  };
  // A new account has nothing to search yet.
  const showTools = Boolean(query.data) && (count > 0 || Boolean(list.q));

  const columns = [
    {
      label: "No.",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Name",
      type: "main",
      sortBy: "name",
      render: (row) => row.name,
    },
    {
      label: "Email",
      type: "email",
      render: (row) => row.email,
    },
    { label: "Phone", render: (row) => row.phoneNumber || "—" },
    { label: "Account no.", render: (row) => row.accountNo },
    {
      label: "Documents",
      type: "number",
      sortBy: "documents",
      render: (row) => (row.documentCount ?? 0).toLocaleString("en"),
    },
    {
      label: "Balance",
      type: "amount",
      sortBy: "balance",
      // What the client's unpaid invoices still owe, in each currency.
      render: (row) =>
        row.balances?.length ? (
          <span className="inline-flex flex-col items-end gap-0.5 stacked:items-start">
            {row.balances.map((balance) => (
              <Amount
                key={balance.currency}
                value={balance.outstanding}
                currency={balance.currency}
              />
            ))}
          </span>
        ) : (
          <span className="text-spot-faint">—</span>
        ),
    },
    {
      label: "View details",
      type: "action",
      render: (row) => (
        <Link
          className={rowAction}
          to={`/single-customer/${row._id}`}
          aria-label={`View ${row.name}`}
        >
          <FiEye aria-hidden="true" />
          <span>View</span>
        </Link>
      ),
    },
    {
      label: "Delete",
      type: "action",
      render: (row) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() => setSelected(row)}
          aria-label={`Delete ${row.name}`}
          disabled={Boolean(deleteRefusal)}
          title={deleteRefusal}
          aria-describedby={deleteRefusal ? "client-delete-refusal" : undefined}
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
      <DataTable
        columns={columns}
        rows={pagination.rows}
        fillRows={pagination.fullPage}
        label="Clients"
        sort={{ field: list.sort, dir: list.dir }}
        onSort={(field) =>
          update({ ...nextSort(SORT_FIELDS, list, field), page: 1 })
        }
        busy={query.isFetching}
      />
      <Pagination pagination={pagination} sizes={PAGE_SIZES} />
    </>
  ) : list.q ? (
    <EmptyState
      title="No clients match."
      text="Try another name, email or account number."
      icon={FiSearch}
      action={
        <button
          type="button"
          className={secondaryButton}
          onClick={() => search("")}
        >
          Clear search
        </button>
      }
    />
  ) : (
    <EmptyState
      title="A place for every client."
      text="Add a client’s contact details to start creating documents for them."
      icon={FiUsers}
    />
  );

  return (
    <WorkspacePage section="Clients" fill>
      <PageHeader
        title="Clients"
        description="The people behind your work. Keep their details close at hand."
        actions={
          <>
            <ClientsTabs />
            <button
              type="button"
              className={secondaryButton}
              onClick={() => setImporting(true)}
              disabled={Boolean(editRefusal)}
              title={editRefusal}
              aria-describedby={editRefusal ? "client-edit-refusal" : undefined}
            >
              <FiUpload aria-hidden="true" /> Import
            </button>
            <button
              type="button"
              className={`${secondaryIconButton} max-[700px]:!flex-none`}
              onClick={() =>
                downloadCsv(
                  clientFilterQuery(list),
                  `invoice-spot-clients-${dateInput()}.csv`,
                )
              }
              disabled={exporting}
              aria-label="Download these clients as CSV"
              title="Download these clients as CSV"
            >
              <FiDownload aria-hidden="true" />
            </button>
            <ActionLink to="/create-customer" reason={editRefusal}>
              <FiPlus aria-hidden="true" /> Add client
            </ActionLink>
          </>
        }
      />
      <RefusalNote id="client-edit-refusal" reason={editRefusal} />
      <RefusalNote id="client-delete-refusal" reason={deleteRefusal} />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel
          title="Your clients"
          fill={rows.length > 0 && !query.isError}
          aside={
            showTools && (
              <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
                <SearchField
                  value={list.q}
                  onSearch={search}
                  label="Search clients"
                  placeholder="Name, email or account"
                  className="w-[240px] max-[700px]:w-full"
                />
                <label className="min-w-0 max-[700px]:flex-1">
                  <span className="sr-only">Sort clients</span>
                  <Select
                    size="toolbar"
                    className="max-[700px]:w-full"
                    value={`${list.sort}:${list.dir}`}
                    onChange={(value) => {
                      const [sort, dir] = value.split(":");
                      update({ sort, dir, page: 1 });
                    }}
                  >
                    {SORT_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </Select>
                </label>
              </div>
            )
          }
        >
          {results}
        </Panel>
      )}
      <ConfirmDialog
        open={Boolean(selected)}
        title="Delete this client?"
        confirmLabel="Delete client"
        onConfirm={remove}
        onClose={() => setSelected(null)}
        busy={deleting}
      >
        {selected?.name} will be permanently deleted.
      </ConfirmDialog>
      <ImportClientsDialog
        open={importing}
        onClose={() => setImporting(false)}
      />
    </WorkspacePage>
  );
};
export default CustomersPage;
