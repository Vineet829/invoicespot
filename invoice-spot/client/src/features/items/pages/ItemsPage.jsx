import { useState } from "react";
import { FiEdit2, FiPackage, FiPlus, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { useDeleteItemMutation, useGetItemsQuery } from "../itemsApiSlice";
import ItemDialog from "../components/ItemDialog";
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
  Amount,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  rowAction,
  rowActionDanger,
} from "../../../components/workspace/pastel";
import { RefusalNote, useBusiness } from "../../business/permissions";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import LineName from "../../gst/LineName";

const ItemsPage = () => {
  useTitle("Items — Invoice Spot");
  const query = useGetItemsQuery();
  const rows = query.data?.items || [];
  const pagination = usePagination(rows, 10);
  // `editing` is the item being edited, or "new" for the add dialog.
  const [editing, setEditing] = useState(null);
  const [removing, setRemoving] = useState(null);
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  const [deleteItem, { isLoading: deleting }] = useDeleteItemMutation();
  const gstMode =
    useGetSettingsQuery().data?.settings?.taxRegime === "GST_INDIA";
  const remove = async () => {
    try {
      await deleteItem(removing._id).unwrap();
      toast.success("Item deleted.");
      setRemoving(null);
    } catch (error) {
      toast.error(errorMessage(error, "The item could not be deleted."));
    }
  };
  const columns = [
    {
      label: "No.",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Name",
      type: "main",
      // In GST mode, with the HSN or SAC code its lines take.
      render: (row) => (
        <LineName name={row.name} hsnSac={gstMode ? row.hsnSac : null} />
      ),
    },
    {
      label: "Description",
      render: (row) => row.description || "—",
    },
    {
      label: "Unit price",
      type: "amount",
      render: (row) => (
        <>
          <Amount value={row.unitPrice} />
          {row.unit && (
            <span className="text-[13px] text-spot-muted"> / {row.unit}</span>
          )}
        </>
      ),
    },
    {
      label: "Tax rate",
      render: (row) => (row.taxRate == null ? "Default" : `${row.taxRate}%`),
    },
    {
      label: "Edit",
      type: "action",
      render: (row) => (
        <button
          type="button"
          className={rowAction}
          onClick={() => setEditing(row)}
          aria-label={`Edit ${row.name}`}
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
          aria-label={`Delete ${row.name}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  // Viewers see the items, but not the ways to change them.
  const shown = editRefusal
    ? columns.filter((column) => !["Edit", "Delete"].includes(column.label))
    : columns;
  return (
    <WorkspacePage section="Items" fill>
      <PageHeader
        title="Items"
        description="The products and services you bill for, with their prices."
        actions={
          <button
            type="button"
            className={primaryButton}
            onClick={() => setEditing("new")}
            disabled={Boolean(editRefusal)}
            title={editRefusal}
            aria-describedby={editRefusal ? "item-edit-refusal" : undefined}
          >
            <FiPlus aria-hidden="true" /> New item
          </button>
        }
      />
      <RefusalNote id="item-edit-refusal" reason={editRefusal} />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel title="Your items" fill={rows.length > 0}>
          {rows.length ? (
            <>
              <DataTable
                columns={shown}
                rows={pagination.rows}
                fillRows={pagination.fullPage}
                label="Items"
              />
              <Pagination pagination={pagination} />
            </>
          ) : (
            <EmptyState
              title="Price it once, reuse it everywhere."
              text="Save the products and services you sell, and the document editor suggests them as you type."
              icon={FiPackage}
            />
          )}
        </Panel>
      )}
      <ItemDialog
        open={Boolean(editing)}
        item={editing === "new" ? null : editing}
        onClose={() => setEditing(null)}
      />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this item?"
        confirmLabel="Delete item"
        onConfirm={remove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        {removing?.name} will be removed from your items. Documents that already
        use it keep their lines.
      </ConfirmDialog>
    </WorkspacePage>
  );
};

export default ItemsPage;
