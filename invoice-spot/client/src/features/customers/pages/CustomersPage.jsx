import { useState } from "react";
import { Link } from "react-router-dom";
import { FiEye, FiPlus, FiTrash2, FiUsers } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { useDeleteCustomerMutation } from "../customersApiSlice";
import { useGetWorkspaceCustomersQuery } from "../../workspace/workspaceApiSlice";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Pagination,
  usePagination,
  ConfirmDialog,
  EmptyState,
  LoadingState,
  ErrorState,
  errorMessage,
} from "../../../components/workspace/Workspace";

const CustomersPage = () => {
  useTitle("Clients — Invoice Spot");
  const query = useGetWorkspaceCustomersQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const rows = query.data?.myCustomers || [];
  const pagination = usePagination(rows);
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
  const columns = [
    {
      label: "No.",
      className: "workspace-cell-index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Name",
      className: "workspace-cell-main",
      render: (row) => row.name,
    },
    {
      label: "Email",
      className: "workspace-cell-email",
      render: (row) => row.email,
    },
    { label: "Phone", render: (row) => row.phoneNumber || "—" },
    { label: "Account no.", render: (row) => row.accountNo },
    {
      label: "View details",
      render: (row) => (
        <Link
          className="workspace-row-action"
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
      render: (row) => (
        <button
          className="workspace-row-action is-danger"
          onClick={() => setSelected(row)}
          aria-label={`Delete ${row.name}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  return (
    <WorkspacePage section="Clients" customerCount={query.data?.totalCustomers}>
      <PageHeader
        title="Clients"
        description="The people behind your work. Keep their details close at hand."
        actions={
          <ActionLink to="/create-customer">
            <FiPlus aria-hidden="true" /> Add client
          </ActionLink>
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel
          title="Your clients"
          aside={
            <span className="workspace-total">
              Total <strong>{query.data?.totalCustomers || 0}</strong>
            </span>
          }
        >
          {rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={pagination.rows}
                label="Clients"
              />
              <Pagination pagination={pagination} />
            </>
          ) : (
            <EmptyState
              title="A place for every client."
              text="Add a client’s contact details to start creating documents for them."
              icon={FiUsers}
            />
          )}
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
    </WorkspacePage>
  );
};
export default CustomersPage;
