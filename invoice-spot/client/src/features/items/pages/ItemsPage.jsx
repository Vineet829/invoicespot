import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FiEdit2, FiPackage, FiPlus, FiSearch, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import { countOf } from "../../../utils/counts";
import { useDeleteItemMutation, useGetItemsQuery } from "../itemsApiSlice";
import { itemListSearch, matchingItems, readItemList } from "../itemFilters";
import ItemDialog from "../components/ItemDialog";
import ItemsTabs from "../components/ItemsTabs";
import ResultLine from "../../documents/components/ResultLine";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  DataTable,
  Pagination,
  usePagination,
  SearchField,
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
  secondaryButton,
} from "../../../components/workspace/pastel";
import { RefusalNote, useBusiness } from "../../business/permissions";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import LineName from "../../gst/LineName";

const NO_ITEMS = [];

const ItemsPage = () => {
  useTitle("Items — Invoice Spot");
  const [list, update] = useListState(readItemList, itemListSearch);
  const query = useGetItemsQuery();
  const items = query.data?.items || NO_ITEMS;
  const rows = useMemo(() => matchingItems(items, list.q), [items, list.q]);
  const pagination = usePagination(rows, 10);
  // A search shows its first page.
  const { setPage } = pagination;
  const search = useCallback(
    (q) => {
      update({ q });
      setPage(0);
    },
    [update, setPage],
  );
  // Clear search is gone once the search is cleared, so focus moves to the
  // search box. The list is searched here, so the box is there at once, or
  // not at all: a business with no items has none, and focus stays put.
  const tools = useRef(null);
  const [refocus, setRefocus] = useState(false);
  const clearSearch = () => {
    search("");
    setRefocus(true);
  };
  useEffect(() => {
    if (!refocus) return;
    setRefocus(false);
    tools.current?.querySelector('input[type="search"]')?.focus();
  }, [refocus]);
  // A new business has nothing to search yet.
  const showTools =
    Boolean(query.data) && (items.length > 0 || Boolean(list.q));
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
          <>
            <ItemsTabs />
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
          </>
        }
      />
      <RefusalNote id="item-edit-refusal" reason={editRefusal} />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !query.data ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel
          title="Your items"
          fill={rows.length > 0}
          aside={
            showTools && (
              <div ref={tools} className="min-w-0 max-[700px]:w-full">
                <SearchField
                  value={list.q}
                  onSearch={search}
                  label="Search items"
                  placeholder="Name or unit"
                  className="w-[240px] max-[700px]:w-full"
                />
              </div>
            )
          }
        >
          {rows.length ? (
            <>
              {list.q && (
                <ResultLine
                  parts={[{ strong: countOf(rows.length, "item", "items") }]}
                  onClear={clearSearch}
                  clearLabel="Clear search"
                />
              )}
              <DataTable
                columns={shown}
                rows={pagination.rows}
                fillRows={pagination.fullPage}
                label="Items"
              />
              <Pagination pagination={pagination} />
            </>
          ) : list.q ? (
            <EmptyState
              title="No items match."
              text="Try another name or unit, or clear the search to see every item."
              icon={FiSearch}
              action={
                <button
                  type="button"
                  className={secondaryButton}
                  onClick={clearSearch}
                >
                  Clear search
                </button>
              }
            />
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
