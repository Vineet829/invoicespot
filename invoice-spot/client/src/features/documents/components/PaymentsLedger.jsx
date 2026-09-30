import { useState } from "react";
import { FiCheck, FiEdit2, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  ConfirmDialog,
  DataTable,
  Field,
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
  useDeletePaymentMutation,
  useUpdatePaymentMutation,
} from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";

const EditPaymentForm = ({ document, payment, busy, onSave, onClose }) => {
  // What is still owed if this payment were removed.
  const limit =
    Number(document.total || 0) -
    Number(document.totalAmountReceived || 0) +
    Number(payment.amountPaid || 0);
  const [values, setValues] = useState({
    datePaid: payment.datePaid || "",
    amountPaid: String(payment.amountPaid ?? ""),
    paymentMethod: payment.paymentMethod || "",
    additionalInfo: payment.additionalInfo || "",
  });
  const update = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({ ...values, amountPaid: Number(values.amountPaid) });
      }}
    >
      <div className="grid grid-cols-2 gap-4 max-[700px]:grid-cols-1">
        <Field
          label="Date paid"
          type="date"
          required
          value={values.datePaid}
          onChange={update("datePaid")}
          disabled={busy}
          data-autofocus
        />
        <Field
          label="Amount paid"
          type="number"
          min="0.01"
          max={limit}
          step="0.01"
          required
          value={values.amountPaid}
          onChange={update("amountPaid")}
          disabled={busy}
          hint={`At most ${money(limit, document.currency)}.`}
        />
        <Field
          label="Payment method"
          as="select"
          required
          value={values.paymentMethod}
          onChange={update("paymentMethod")}
          disabled={busy}
        >
          {PAYMENT_METHODS.map((method) => (
            <option key={method}>{method}</option>
          ))}
        </Field>
        <Field
          label="Additional info"
          maxLength={255}
          value={values.additionalInfo}
          onChange={update("additionalInfo")}
          disabled={busy}
        />
      </div>
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
          {busy ? "Saving…" : "Save payment"}
        </button>
      </div>
    </form>
  );
};

// The payments recorded on a document, each with edit and delete. The server
// recalculates the amount received and the status after every change.
const PaymentsLedger = ({ document }) => {
  const [editing, setEditing] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [update, { isLoading: saving }] = useUpdatePaymentMutation();
  const [remove, { isLoading: deleting }] = useDeletePaymentMutation();
  const save = async (values) => {
    try {
      await update({
        id: document._id,
        paymentId: editing._id,
        ...values,
      }).unwrap();
      toast.success("Payment updated.");
      setEditing(null);
    } catch (error) {
      toast.error(errorMessage(error, "The payment could not be updated."));
    }
  };
  const confirmRemove = async () => {
    try {
      await remove({ id: document._id, paymentId: removing._id }).unwrap();
      toast.success("Payment deleted.");
      setRemoving(null);
    } catch (error) {
      toast.error(errorMessage(error, "The payment could not be deleted."));
    }
  };
  const columns = [
    {
      label: "Date paid",
      type: "main",
      render: (payment) => dateLabel(payment.datePaid),
    },
    { label: "Payment method", render: (payment) => payment.paymentMethod },
    {
      label: "Additional info",
      render: (payment) => payment.additionalInfo || "—",
    },
    {
      label: "Amount paid",
      type: "amount",
      render: (payment) => (
        <Amount value={payment.amountPaid} currency={document.currency} />
      ),
    },
    {
      label: "Edit",
      type: "action",
      render: (payment) => (
        <button
          type="button"
          className={rowAction}
          onClick={() => setEditing(payment)}
          aria-label={`Edit the payment of ${dateLabel(payment.datePaid)}`}
        >
          <FiEdit2 aria-hidden="true" />
          <span>Edit</span>
        </button>
      ),
    },
    {
      label: "Delete",
      type: "action",
      render: (payment) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() => setRemoving(payment)}
          aria-label={`Delete the payment of ${dateLabel(payment.datePaid)}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Delete</span>
        </button>
      ),
    },
  ];
  return (
    <>
      <DataTable
        columns={columns}
        rows={document.paymentRecords || []}
        label="Payments"
      />
      <Dialog
        open={Boolean(editing)}
        title="Edit payment"
        onClose={() => setEditing(null)}
        busy={saving}
        dismissible={false}
        size="medium"
      >
        {editing && (
          <EditPaymentForm
            key={editing._id}
            document={document}
            payment={editing}
            busy={saving}
            onSave={save}
            onClose={() => setEditing(null)}
          />
        )}
      </Dialog>
      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this payment?"
        confirmLabel="Delete payment"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        The payment of{" "}
        {removing && money(removing.amountPaid, document.currency)} on{" "}
        {removing && dateLabel(removing.datePaid)} will be deleted, and the
        amount still owed goes up by the same amount.
      </ConfirmDialog>
    </>
  );
};

export default PaymentsLedger;
