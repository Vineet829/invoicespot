import { useState } from "react";
import { useSelector } from "react-redux";
import {
  FiPlay,
  FiShield,
  FiShieldOff,
  FiSlash,
  FiTrash2,
  FiUsers,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useDeleteUserMutation,
  useDeactivateUserMutation,
} from "../usersApiSlice";
import {
  useGetAdminStatsQuery,
  useGetUsersPageQuery,
  useReactivateUserMutation,
  useSetUserAdminMutation,
} from "../adminApiSlice";
import AdminTabs from "../components/AdminTabs";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  DataTable,
  Pagination,
  ConfirmDialog,
  EmptyState,
  LoadingState,
  ErrorState,
  Badge,
  SearchField,
  dateLabel,
  errorMessage,
  serverPagination,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  rowAction,
  rowActionDanger,
} from "../../../components/workspace/pastel";

const PROVIDERS = { email: "Email", google: "Google", github: "GitHub" };

const STATS = [
  ["accounts", "Accounts"],
  ["active", "Active"],
  ["admins", "Admins"],
  ["unverified", "Unverified"],
  ["joinedLast30Days", "New, last 30 days"],
  ["signedInLast30Days", "Signed in, last 30 days"],
];

// What each confirmed action does, and how it is worded.
const CONFIRMATIONS = {
  promote: {
    title: "Make this account an admin?",
    label: "Make admin",
    text: (row) =>
      `${row.email} will be able to see and change every account, and read the audit log.`,
  },
  demote: {
    title: "Remove this admin?",
    label: "Remove admin",
    text: (row) => `${row.email} will become a regular user at once.`,
  },
  deactivate: {
    title: "Deactivate this account?",
    label: "Deactivate",
    text: (row) =>
      `${row.email} is signed out everywhere and can't sign in until reactivated.`,
  },
  delete: {
    title: "Delete this account?",
    label: "Delete account",
    text: (row) =>
      `${row.email} and everything in it will be permanently deleted.`,
  },
};

const isAdmin = (row) => (row.roles || []).includes("Admin");

const UsersListPage = () => {
  useTitle("Administration — Invoice Spot");
  const me = useSelector((state) => state.auth.user?.username);
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const query = useGetUsersPageQuery(
    { page: page + 1, size, q },
    { refetchOnMountOrArgChange: true },
  );
  const stats = useGetAdminStatsQuery(undefined, {
    refetchOnMountOrArgChange: true,
  });
  const rows = query.data?.users || [];
  const pagination = serverPagination({
    rows,
    count: query.data?.count || 0,
    page,
    size,
    setPage,
    setSize: (next) => {
      setSize(next);
      setPage(0);
    },
  });
  const [confirming, setConfirming] = useState(null);
  const [remove, { isLoading: deleting }] = useDeleteUserMutation();
  const [deactivate, { isLoading: deactivating }] = useDeactivateUserMutation();
  const [reactivate, { isLoading: reactivating }] = useReactivateUserMutation();
  const [setAdmin, { isLoading: changingRole }] = useSetUserAdminMutation();
  const busy = deleting || deactivating || reactivating || changingRole;
  const run = async (action, success, failure) => {
    try {
      await action().unwrap();
      toast.success(success);
      return true;
    } catch (error) {
      toast.error(errorMessage(error, failure));
      return false;
    }
  };
  const confirm = async () => {
    const { kind, row } = confirming;
    const done = await {
      promote: () =>
        run(
          () => setAdmin({ id: row._id, admin: true }),
          `${row.username} is now an admin.`,
          "The account could not be made an admin.",
        ),
      demote: () =>
        run(
          () => setAdmin({ id: row._id, admin: false }),
          `${row.username} is no longer an admin.`,
          "The admin role could not be removed.",
        ),
      deactivate: () =>
        run(
          () => deactivate(row._id),
          `${row.username} was deactivated.`,
          "The account could not be deactivated.",
        ),
      delete: () =>
        run(
          () => remove(row._id),
          `${row.username} was deleted.`,
          "The account could not be deleted.",
        ),
    }[kind]();
    if (done) setConfirming(null);
  };
  // Why an action can't be taken on this row, if it can't.
  const blocked = (row) =>
    row.username === me
      ? "That’s your own account"
      : row.demo
        ? "The shared demo account"
        : null;
  const columns = [
    {
      label: "Account",
      type: "main",
      render: (row) => (
        <span className="flex flex-col gap-0.5">
          <span>
            {[row.firstName, row.lastName].filter(Boolean).join(" ") ||
              row.username}
          </span>
          <span className={`font-normal ${panelNote}`}>{row.email}</span>
        </span>
      ),
    },
    { label: "Username", render: (row) => row.username },
    {
      label: "Signs in with",
      render: (row) => PROVIDERS[row.provider] || row.provider,
    },
    {
      label: "Status",
      // The badges keep to one line while the list is a table.
      nowrap: true,
      render: (row) => (
        <span className="flex flex-wrap gap-1.5 tabled:flex-nowrap">
          {isAdmin(row) && <Badge tone="paid">Admin</Badge>}
          <Badge tone={row.active ? "neutral" : "unpaid"}>
            {row.active ? "Active" : "Deactivated"}
          </Badge>
          {!row.isEmailVerified && <Badge tone="partial">Unverified</Badge>}
        </span>
      ),
    },
    { label: "Joined", render: (row) => dateLabel(row.createdAt) },
    {
      label: "Role",
      type: "action",
      render: (row) => {
        const reason = blocked(row);
        const admin = isAdmin(row);
        return (
          <button
            type="button"
            className={rowAction}
            onClick={() =>
              setConfirming({ kind: admin ? "demote" : "promote", row })
            }
            disabled={busy || Boolean(reason) || (!admin && !row.active)}
            aria-label={
              reason
                ? `${reason} can’t change role`
                : `${admin ? "Remove admin role from" : "Make admin"} ${row.username}`
            }
          >
            {admin ? (
              <FiShieldOff aria-hidden="true" />
            ) : (
              <FiShield aria-hidden="true" />
            )}
            <span>{admin ? "Remove admin" : "Make admin"}</span>
          </button>
        );
      },
    },
    {
      label: "Access",
      type: "action",
      render: (row) => {
        const reason = blocked(row);
        return row.active ? (
          <button
            type="button"
            className={rowActionDanger}
            onClick={() => setConfirming({ kind: "deactivate", row })}
            disabled={busy || Boolean(reason)}
            aria-label={
              reason
                ? `${reason} can’t be deactivated`
                : `Deactivate ${row.username}`
            }
          >
            <FiSlash aria-hidden="true" />
            <span>Deactivate</span>
          </button>
        ) : (
          <button
            type="button"
            className={rowAction}
            onClick={() =>
              run(
                () => reactivate(row._id),
                `${row.username} can sign in again.`,
                "The account could not be reactivated.",
              )
            }
            disabled={busy}
            aria-label={`Reactivate ${row.username}`}
          >
            <FiPlay aria-hidden="true" />
            <span>Reactivate</span>
          </button>
        );
      },
    },
    {
      label: "Delete",
      type: "action",
      render: (row) => {
        const reason = blocked(row);
        return (
          <button
            type="button"
            className={rowActionDanger}
            onClick={() => setConfirming({ kind: "delete", row })}
            disabled={busy || Boolean(reason)}
            aria-label={
              reason ? `${reason} can’t be deleted` : `Delete ${row.username}`
            }
          >
            <FiTrash2 aria-hidden="true" />
            <span>Delete</span>
          </button>
        );
      },
    },
  ];
  const dialog = confirming && CONFIRMATIONS[confirming.kind];
  return (
    <WorkspacePage section="Administration" fill>
      <PageHeader
        title="Administration"
        description="View user accounts and manage their access."
        actions={<AdminTabs />}
      />
      <dl
        className="m-0 grid grid-cols-6 gap-3 max-[1359px]:grid-cols-3 max-[700px]:grid-cols-2"
        aria-busy={stats.isFetching || undefined}
      >
        {STATS.map(([key, label]) => (
          <div
            key={key}
            className="min-w-0 rounded-[20px] border border-white/90 bg-white/60 px-4 py-3.5"
          >
            <dt className={panelNote}>{label}</dt>
            <dd className="m-0 mt-1 font-display text-[26px] font-light tracking-[-0.03em] text-spot-ink">
              {stats.data ? stats.data[key].toLocaleString("en") : "—"}
            </dd>
          </div>
        ))}
      </dl>
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel fill={rows.length > 0}>
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 compact:mb-4">
            <h2 className="font-display text-[22px] font-light tracking-[-0.02em] text-spot-ink">
              Users
            </h2>
            <SearchField
              value={q}
              onSearch={(next) => {
                setQ(next);
                setPage(0);
              }}
              label="Search accounts"
              placeholder="Name, email or username"
              className="w-[280px] max-[700px]:w-full"
            />
          </div>
          {rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={rows}
                fillRows={pagination.fullPage}
                busy={query.isFetching}
                label="Users"
                wide
              />
              <Pagination pagination={pagination} sizes={[10, 25, 50, 100]} />
            </>
          ) : (
            <EmptyState
              title={q ? "No account matches." : "No users to display."}
              text={
                q
                  ? "Try a name, an email or a username."
                  : "User accounts will appear here."
              }
              icon={FiUsers}
            />
          )}
        </Panel>
      )}
      <ConfirmDialog
        open={Boolean(dialog)}
        title={dialog?.title}
        confirmLabel={dialog?.label}
        onConfirm={confirm}
        onClose={() => setConfirming(null)}
        busy={busy}
      >
        {dialog?.text(confirming.row)}
      </ConfirmDialog>
    </WorkspacePage>
  );
};

export default UsersListPage;
