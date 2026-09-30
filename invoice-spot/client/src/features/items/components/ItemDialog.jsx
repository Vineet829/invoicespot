import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { Field, errorMessage } from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useCreateItemMutation, useUpdateItemMutation } from "../itemsApiSlice";

const ItemForm = ({ item, busy, onSave, onClose }) => {
  const [values, setValues] = useState({
    name: item?.name || "",
    description: item?.description || "",
    unitPrice: item ? String(item.unitPrice) : "",
  });
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({
          name: values.name.trim(),
          description: values.description,
          unitPrice: Number(values.unitPrice) || 0,
        });
      }}
    >
      <Field
        label="Name"
        required
        maxLength={255}
        value={values.name}
        onChange={change("name")}
        disabled={busy}
        data-autofocus
      />
      <Field
        label="Unit price"
        type="number"
        min="0"
        step="0.0001"
        required
        value={values.unitPrice}
        onChange={change("unitPrice")}
        disabled={busy}
      />
      <Field
        label="Description"
        as="textarea"
        maxLength={1000}
        value={values.description}
        onChange={change("description")}
        disabled={busy}
        hint="For your own reference; documents show only the name."
      />
      <div className="mt-3 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={busy}>
          <FiCheck aria-hidden="true" />
          {busy ? "Saving…" : "Save item"}
        </button>
      </div>
    </form>
  );
};

// Adds an item, or edits one when `item` is given.
const ItemDialog = ({ open, item, onClose }) => {
  const [create, { isLoading: creating }] = useCreateItemMutation();
  const [update, { isLoading: updating }] = useUpdateItemMutation();
  const busy = creating || updating;
  const save = async (values) => {
    try {
      const result = item
        ? await update({ id: item._id, ...values }).unwrap()
        : await create(values).unwrap();
      toast.success(result.message || "Item saved.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The item could not be saved."));
    }
  };
  return (
    <Dialog
      open={open}
      title={item ? "Edit item" : "New item"}
      onClose={onClose}
      busy={busy}
      dismissible={false}
    >
      {open && (
        <ItemForm
          key={item?._id || "new"}
          item={item}
          busy={busy}
          onSave={save}
          onClose={onClose}
        />
      )}
    </Dialog>
  );
};

export default ItemDialog;
