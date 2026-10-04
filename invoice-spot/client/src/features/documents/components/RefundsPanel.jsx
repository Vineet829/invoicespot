import { useState } from "react";
import { FiCheck, FiEdit2, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  ConfirmDialog,
  DataTable,
  Field,
  Panel,
  dateInput,
  dateLabel,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  rowAction,
  rowActionDanger,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useCreateRefundMutation,
  useDeleteRefundMutation,
  useUpdateRefundMutation,
} from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";

// The fields of a refund, new or changed; the amount is at most `limit`.
const RefundFields = ({ values, update, limit, currency, busy, columns }) => (
  <div className={`grid gap-5 ${columns} max-[700px]:grid-cols-1`}>
    <Field
      label="Date refunded"
      type="date"
      required
      max={dateInput()}
      value={values.dateRefunded}
      onChange={update("dateRefunded")}
      disabled={busy}
    />
    <Field
      label="Amount refunded"
      type="number"
      min="0.01"
      max={limit}
      step="0.01"
      required
      value={values.amount}
      onChange={update("amount")}
      hint={`At most ${money(limit, currency)}.`}
      disabled={busy}
    />
    <Field
      label="Refund method"
      as="select"
      required
      value={values.paymentMethod}
      onChange={update("paymentMethod")}
      disabled={busy}
    >
      <option value="">Choose a method</option>
      {PAYMENT_METHODS.map((method) => (
        <option key={method}>{method}</option>
      ))}
    </Field>
    <Field
      label="Reference"
      maxLength={255}
      value={values.reference}
      onChange={update("reference")}
      disabled={busy}
    />
  </div>
);

const useValues = (initial) => {
  const [values, setValues] = useState(initial);
  const update = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  return [values, update, setValues];
};

const blank = () => ({
  dateRefunded: dateInput(),
  amount: "",
  paymentMethod: "",
  reference: "",
});

const RefundForm = ({ document, due }) => {
  const [values, update, setValues] = useValues(blank());
  const [create, { isLoading: busy }] = useCreateRefundMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      await create({
        id: document._id,
        ...values,
        amount: Number(values.amount),
      }).unwrap();
      toast.success("The refund was recorded.");
      setValues(blank());
    } catch (error) {
      toast.error(errorMessage(error, "The refund could not be recorded."));
    }
  };
  return (
    <form onSubmit={submit}>
      <RefundFields
        values={values}
        update={update}
        limit={due}
        currency={document.currency}
        busy={busy}
        columns="grid-cols-4 max-[1359px]:grid-cols-2"
      />
      <div className="mt-5 flex justify-end max-[700px]:[&>*]:flex-1">
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || !values.amount || !values.paymentMethod}
        >
          <FiCheck aria-hidden="true" />
          {busy ? "Recording…" : "Record refund"}
        </button>
      </div>
    </form>
  );
};

const EditRefundForm = ({ document, refund, due, busy, onSave, onClose }) => {
  const [values, update] = useValues({
    dateRefunded: refund.dateRefunded || "",
    amount: String(refund.amount ?? ""),
    paymentMethod: refund.paymentMethod || "",
    reference: refund.reference || "",
  });
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({ ...values, amount: Number(values.amount) });
      }}
    >
      <RefundFields
        values={values}
        update={update}
        limit={Math.round((due + Number(refund.amount)) * 100) / 100}
        currency={document.currency}
        busy={busy}
        columns="grid-cols-2"
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
          {busy ? "Saving…" : "Save refund"}
        </button>
      </div>
    </form>
  );
};

// Money paid back to the client when a credit note leaves this document
// overpaid. The form shows while something is due back; the server keeps
// every refund within it.
const RefundsPanel = ({ document, refusal }) => {
  const due = Number(document.ledger?.refundDue || 0);
  const refunds = document.refunds || [];
  const [editing, setEditing] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [update, { isLoading: saving }] = useUpdateRefundMutation();
  const [remove, { isLoading: deleting }] = useDeleteRefundMutation();
  const save = async (values) => {
    try {
      await update({
        id: document._id,
        refundId: editing._id,
        ...values,
      }).unwrap();
      toast.success("The refund was updated.");
      setEditing(null);
    } catch (error) {
      toast.error(errorMessage(error, "The refund could not be updated."));
    }
  };
  const confirmRemove = async () => {
    try {
      await remove({ id: document._id, refundId: removing._id }).unwrap();
      toast.success("The refund was deleted.");
      setRemoving(null);
    } catch (error) {
      toast.error(errorMessage(error, "The refund could not be deleted."));
    }
  };
  const columns = [
    {
      label: "Date refunded",
      type: "main",
      render: (refund) => dateLabel(refund.dateRefunded),
    },
    { label: "Method", render: (refund) => refund.paymentMethod },
    { label: "Reference", render: (refund) => refund.reference || "—" },
    {
      label: "Amount",
      type: "amount",
      render: (refund) => (
        <Amount value={refund.amount} currency={document.currency} />
      ),
    },
    {
      label: "Edit",
      type: "action",
      render: (refund) => (
        <button
          type="button"
          className={rowAction}
          onClick={() => setEditing(refund)}
          aria-label={`Edit the refund of ${dateLabel(refund.dateRefunded)}`}
        >
          <FiEdit2 aria-hidden="true" />
          <span>Edit</span>
        </button>
      ),
    },
    {
      label: "Delete",
      type: "action",
      render: (refund) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() => setRemoving(refund)}
          aria-label={`Delete the refund of ${dateLabel(refund.dateRefunded)}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  return (
    <Panel
      title="Refunds"
      description={
        due > 0
          ? `Due back to ${document.customer?.name || "your customer"}: ${money(due, document.currency)}`
          : "Money paid back to the client."
      }
    >
      <div className="flex flex-col gap-6">
        {refunds.length > 0 && (
          <DataTable
            columns={
              refusal
                ? columns.filter(
                    (column) => !["Edit", "Delete"].includes(column.label),
                  )
                : columns
            }
            rows={refunds}
            label="Refunds"
          />
        )}
        {due > 0 && !refusal && <RefundForm document={document} due={due} />}
      </div>
      <Dialog
        open={Boolean(editing)}
        title="Edit refund"
        onClose={() => setEditing(null)}
        busy={saving}
        dismissible={false}
        size="medium"
      >
        {editing && (
          <EditRefundForm
            key={editing._id}
            document={document}
            refund={editing}
            due={due}
            busy={saving}
            onSave={save}
            onClose={() => setEditing(null)}
          />
        )}
      </Dialog>
      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this refund?"
        confirmLabel="Delete refund"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        The refund of {removing && money(removing.amount, document.currency)} on{" "}
        {removing && dateLabel(removing.dateRefunded)} will be deleted, and the
        amount due back goes up by the same amount.
      </ConfirmDialog>
    </Panel>
  );
};

export default RefundsPanel;
