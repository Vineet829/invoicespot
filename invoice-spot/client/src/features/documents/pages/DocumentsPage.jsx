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
  statusTone,
  dateLabel,
  money,
  errorMessage,
} from "../../../components/workspace/Workspace";

const DocumentsPage = () => {
  useTitle("Documents — Invoice Spot");
  const query = useGetDashboardDocumentsQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const rows = query.data?.myDocuments || [];
  const pagination = usePagination(rows);
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
      className: "workspace-cell-index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Document no.",
      className: "workspace-cell-main",
      render: (row) => row.documentNumber,
    },
    { label: "Type", render: (row) => <Badge>{row.documentType}</Badge> },
    { label: "Customer", render: (row) => row.customer?.name || "—" },
    {
      label: "Amount",
      className: "workspace-cell-amount",
      render: (row) => money(row.total),
    },
    { label: "Due date", render: (row) => dateLabel(row.dueDate) },
    {
      label: "Payment status",
      render: (row) => (
        <Badge tone={statusTone(row.status)}>{row.status}</Badge>
      ),
    },
    {
      label: "View",
      render: (row) => (
        <Link
          className="workspace-row-action"
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
      render: (row) => (
        <button
          className="workspace-row-action is-danger"
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
    <WorkspacePage
      section="Documents"
      documentCount={query.data?.totalDocuments}
    >
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
        <Panel
          title="Your documents"
          aside={
            <span className="workspace-total">
              Total <strong>{query.data?.totalDocuments || 0}</strong>
            </span>
          }
        >
          {rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={pagination.rows}
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
