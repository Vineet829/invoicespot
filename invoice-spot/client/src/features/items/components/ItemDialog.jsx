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
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import {
  HSN_SAC_PATTERN,
  isValidHsnSac,
  normalizeHsnSac,
} from "../../gst/hsnSac";

const ItemForm = ({ item, busy, onSave, onClose }) => {
  const [values, setValues] = useState({
    name: item?.name || "",
    description: item?.description || "",
    unitPrice: item ? String(item.unitPrice) : "",
    taxRate: item?.taxRate == null ? "" : String(item.taxRate),
    unit: item?.unit || "",
    hsnSac: item?.hsnSac || "",
  });
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  // In GST mode an item keeps the HSN or SAC code its lines are taxed
  // under; outside it the code is left as it is.
  const gstMode =
    useGetSettingsQuery().data?.settings?.taxRegime === "GST_INDIA";
  const code = values.hsnSac.trim();
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({
          name: values.name.trim(),
          description: values.description,
          unitPrice: Number(values.unitPrice) || 0,
          taxRate: values.taxRate,
          unit: values.unit,
          ...(gstMode ? { hsnSac: normalizeHsnSac(values.hsnSac) } : {}),
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
      <div className="grid grid-cols-2 gap-4 max-[480px]:grid-cols-1">
        <Field
          label="Tax rate (%)"
          type="number"
          min="0"
          max="100"
          step="any"
          value={values.taxRate}
          onChange={change("taxRate")}
          disabled={busy}
          hint="Left empty, lines take your default rate."
        />
        <Field
          label="Unit"
          maxLength={20}
          value={values.unit}
          onChange={change("unit")}
          disabled={busy}
          hint="What a quantity counts, such as hours."
        />
      </div>
      {gstMode && (
        <Field
          label="HSN/SAC code"
          inputMode="numeric"
          maxLength={12}
          pattern={HSN_SAC_PATTERN}
          title="4 to 8 digits"
          value={values.hsnSac}
          onChange={change("hsnSac")}
          disabled={busy}
          error={code && !isValidHsnSac(code) ? "Enter 4 to 8 digits." : null}
          hint="Filled in on its lines; GST invoices total their tax by it."
        />
      )}
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
