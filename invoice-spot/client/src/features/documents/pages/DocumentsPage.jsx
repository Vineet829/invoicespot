import { useState } from "react";
import { Link } from "react-router-dom";
import { FiEye, FiFileText, FiPlus, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { useGetDashboardDocumentsQuery } from "../../dashboard/dashboardApiSlice";
import { useDeleteDocMutation } from "../documentsApiSlice";
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
  Badge,
  StatusBadge,
  Amount,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  rowAction,
  rowActionDanger,
} from "../../../components/workspace/pastel";

const DocumentsPage = () => {
  useTitle("Documents — Invoice Spot");
  const query = useGetDashboardDocumentsQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const rows = query.data?.myDocuments || [];
  const pagination = usePagination(rows, 10);
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
  const columns = [
    {
      label: "#",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Document no.",
      type: "main",
      render: (row) => row.documentNumber,
    },
    { label: "Type", render: (row) => <Badge>{row.documentType}</Badge> },
    { label: "Customer", render: (row) => row.customer?.name || "—" },
    {
      label: "Amount",
      type: "amount",
      render: (row) => <Amount value={row.total} />,
    },
    { label: "Due date", render: (row) => dateLabel(row.dueDate) },
    {
      label: "Payment status",
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      label: "View",
      type: "action",
      render: (row) => (
        <Link
          className={rowAction}
          to={`/document/${row._id}`}
          aria-label={`View ${row.documentNumber}`}
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
          aria-label={`Delete ${row.documentNumber}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  return (
    <WorkspacePage section="Documents" fill>
      <PageHeader
        title="Documents"
        description="Invoices, quotations, and receipts. All your paperwork in one place."
        actions={
          <ActionLink to="/create-doc">
            <FiPlus aria-hidden="true" /> Create document
          </ActionLink>
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel title="Your documents" fill={rows.length > 0}>
          {rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={pagination.rows}
                fillRows={pagination.fullPage}
                label="Documents"
              />
              <Pagination pagination={pagination} />
            </>
          ) : (
            <EmptyState
              title="Your work, ready to invoice."
              text="Create a document to keep your invoices, quotations, and receipts together."
              icon={FiFileText}
            />
          )}
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
        {selected?.documentNumber} will be permanently deleted.
      </ConfirmDialog>
    </WorkspacePage>
  );
};
export default DocumentsPage;
