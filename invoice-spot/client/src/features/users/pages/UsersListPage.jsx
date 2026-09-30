import { useState } from "react";
import { FiTrash2, FiUsers } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useDeleteUserMutation,
  useDeactivateUserMutation,
} from "../usersApiSlice";
import { useGetWorkspaceUsersQuery } from "../../workspace/workspaceApiSlice";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  DataTable,
  Pagination,
  usePagination,
  ConfirmDialog,
  EmptyState,
  LoadingState,
  ErrorState,
  Badge,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { rowActionDanger } from "../../../components/workspace/pastel";
const UsersListPage = () => {
  useTitle("Administration — Invoice Spot");
  const query = useGetWorkspaceUsersQuery(undefined, {
    pollingInterval: 60000,
    refetchOnFocus: true,
    refetchOnMountOrArgChange: true,
  });
  const rows = query.data?.users || [];
  const pagination = usePagination(rows, 10);
  const [selected, setSelected] = useState(null);
  const [busyUser, setBusyUser] = useState(null);
  const [remove, { isLoading: deleting }] = useDeleteUserMutation();
  const [deactivate] = useDeactivateUserMutation();
  const deleteUser = async () => {
    try {
      await remove(selected._id).unwrap();
      toast.success("User deleted successfully.");
      setSelected(null);
    } catch (error) {
      toast.error(errorMessage(error, "The user could not be deleted."));
    }
  };
  const deactivateUser = async (row) => {
    setBusyUser(row._id);
    try {
      await deactivate(row._id).unwrap();
      toast.success("User deactivated.");
    } catch (error) {
      toast.error(errorMessage(error, "The user could not be deactivated."));
    } finally {
      setBusyUser(null);
    }
  };
  const columns = [
    {
      label: "Email",
      type: "main",
      render: (row) => row.email,
    },
    { label: "Username", render: (row) => row.username },
    { label: "Provider", render: (row) => row.provider },
    {
      label: "Email verified",
      render: (row) => (
        <Badge tone={row.isEmailVerified ? "paid" : "neutral"}>
          {row.isEmailVerified ? "Yes" : "No"}
        </Badge>
      ),
    },
    { label: "Roles", render: (row) => (row.roles || []).join(", ") },
    { label: "Joined", render: (row) => dateLabel(row.createdAt) },
    {
      label: "Active user",
      render: (row) => (
        <label className="inline-flex items-center gap-2 whitespace-nowrap text-spot-body [&>input]:m-0 [&>input]:size-4 [&>input]:accent-spot-violet">
          <input
            type="checkbox"
            checked={Boolean(row.active)}
            disabled={!row.active || busyUser === row._id}
            onChange={() => deactivateUser(row)}
            aria-label={`Deactivate ${row.username}`}
          />
          {row.active ? "Active" : "Inactive"}
        </label>
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
          aria-label={`Delete ${row.username}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  return (
    <WorkspacePage section="Administration" fill>
      <PageHeader
        title="Administration"
        description="View user accounts and manage their access."
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel title="Users" fill={rows.length > 0}>
          {rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={pagination.rows}
                fillRows={pagination.fullPage}
                label="Users"
              />
              <Pagination pagination={pagination} />
            </>
          ) : (
            <EmptyState
              title="No users to display."
              text="User accounts will appear here."
              icon={FiUsers}
            />
          )}
        </Panel>
      )}
      <ConfirmDialog
        open={Boolean(selected)}
        title="Delete this user?"
        confirmLabel="Delete user"
        onConfirm={deleteUser}
        onClose={() => setSelected(null)}
        busy={deleting}
      >
        {selected?.username} will be permanently deleted.
      </ConfirmDialog>
    </WorkspacePage>
  );
};
export default UsersListPage;
