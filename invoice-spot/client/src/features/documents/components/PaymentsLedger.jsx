import { useState } from "react";
import { FiCheck, FiDownload, FiEdit2, FiMail, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  ConfirmDialog,
  DataTable,
  Field,
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
  textLink,
} from "../../../components/workspace/pastel";
import {
  useDeletePaymentMutation,
  useGetReceiptPdfMutation,
  useUpdatePaymentMutation,
} from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";
import { TAX_WITHHELD_HINT, TAX_WITHHELD_LABEL, cents } from "../taxWithheld";
import { saveFile } from "../useDocumentPdf";
import EmailReceiptDialog from "./EmailReceiptDialog";

const DEMO_EMAIL_NOTE = "Emailing is turned off for the shared demo account.";

const EditPaymentForm = ({ document, payment, busy, onSave, onClose }) => {
  // What is still owed if this payment were removed.
  const limit = cents(
    Number(document.ledger?.balanceDue || 0) +
      Number(payment.amountPaid || 0) +
      Number(payment.tdsAmount || 0),
  );
  const [values, setValues] = useState({
    datePaid: payment.datePaid || "",
    amountPaid: String(payment.amountPaid ?? ""),
    // The tax the client withheld, or null while its field is hidden.
    tdsAmount: Number(payment.tdsAmount) > 0 ? String(payment.tdsAmount) : null,
    paymentMethod: payment.paymentMethod || "",
    additionalInfo: payment.additionalInfo || "",
  });
  const withholding = values.tdsAmount !== null;
  const settles = cents(values.amountPaid) + cents(values.tdsAmount);
  const update = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave({
          ...values,
          amountPaid: Number(values.amountPaid),
          tdsAmount: Number(values.tdsAmount),
        });
      }}
    >
      <div className="grid grid-cols-2 gap-4 max-[700px]:grid-cols-1">
        <Field
          label="Date paid"
          type="date"
          required
          max={dateInput()}
          value={values.datePaid}
          onChange={update("datePaid")}
          disabled={busy}
          data-autofocus
        />
        <Field
          label="Amount paid"
          type="number"
          min={withholding ? "0" : "0.01"}
          max={cents(limit - cents(values.tdsAmount))}
          step="0.01"
          required={!withholding}
          value={values.amountPaid}
          onChange={update("amountPaid")}
          disabled={busy}
          hint={`At most ${money(limit, document.currency)}${withholding ? " with the tax withheld" : ""}.`}
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
        {withholding && (
          <Field
            id="edit-payment-tds"
            label={TAX_WITHHELD_LABEL}
            type="number"
            min="0"
            max={cents(limit - cents(values.amountPaid))}
            step="0.01"
            hint={TAX_WITHHELD_HINT}
            value={values.tdsAmount}
            onChange={update("tdsAmount")}
            disabled={busy}
          />
        )}
      </div>
      <button
        type="button"
        className={`${textLink} self-start bg-transparent p-0 text-[13px] coarse:min-h-11`}
        aria-expanded={withholding}
        aria-controls={withholding ? "edit-payment-tds" : undefined}
        onClick={() =>
          setValues((current) => ({
            ...current,
            tdsAmount: withholding ? null : "",
          }))
        }
        disabled={busy}
      >
        {withholding
          ? "Remove the tax withheld"
          : "Add tax withheld by the client (TDS)"}
      </button>
      <div className="mt-3 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || settles <= 0}
        >
          <FiCheck aria-hidden="true" />
          {busy ? "Saving…" : "Save payment"}
        </button>
      </div>
    </form>
  );
};

const DeletedPayment = ({ payment, document }) => {
  const withheld = Number(payment.tdsAmount) > 0;
  return (
    <>
      The payment of {money(payment.amountPaid, document.currency)}
      {withheld &&
        `, with ${money(payment.tdsAmount, document.currency)} of tax withheld,`}{" "}
      on {dateLabel(payment.datePaid)} will be deleted, and the amount still
      owed goes up by{" "}
      {withheld
        ? money(
            cents(payment.amountPaid) + cents(payment.tdsAmount),
            document.currency,
          )
        : "the same amount"}
      .
    </>
  );
};

// The payments recorded on a document, each with its receipt, edit and
// delete. The server recalculates the amount received and the status after
// every change. Each receipt keeps the number its payment was given.
const PaymentsLedger = ({ document, demo = false, refusal, deleteRefusal }) => {
  const [editing, setEditing] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [emailingReceipt, setEmailingReceipt] = useState(null);
  const [downloading, setDownloading] = useState(null);
  const [getReceipt] = useGetReceiptPdfMutation();
  const downloadReceipt = async (payment) => {
    setDownloading(payment._id);
    try {
      const url = await getReceipt({
        id: document._id,
        paymentId: payment._id,
      }).unwrap();
      saveFile(
        url,
        `Receipt-${document.documentNumber}-P${payment.receiptNumber}.pdf`.replace(
          /[^A-Za-z0-9._-]/g,
          "-",
        ),
      );
    } catch (error) {
      toast.error(errorMessage(error, "The receipt could not be prepared."));
    } finally {
      setDownloading(null);
    }
  };
  const payments = document.paymentRecords || [];
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
    // Only once some payment had tax withheld from it.
    ...(payments.some((payment) => Number(payment.tdsAmount) > 0)
      ? [
          {
            label: "Tax withheld",
            type: "amount",
            render: (payment) => (
              <Amount value={payment.tdsAmount} currency={document.currency} />
            ),
          },
        ]
      : []),
    {
      label: "Receipt",
      type: "action",
      render: (payment) => (
        <span className="inline-flex gap-1.5">
          <button
            type="button"
            className={rowAction}
            onClick={() => downloadReceipt(payment)}
            disabled={downloading === payment._id}
            aria-busy={downloading === payment._id}
            aria-label={`Download the receipt for the payment of ${dateLabel(payment.datePaid)}`}
          >
            <FiDownload aria-hidden="true" />
            <span>Receipt</span>
          </button>
          <button
            type="button"
            className={rowAction}
            onClick={() => setEmailingReceipt(payment._id)}
            disabled={demo || Boolean(refusal)}
            title={refusal || (demo ? DEMO_EMAIL_NOTE : "Email receipt")}
            aria-label={`Email the receipt for the payment of ${dateLabel(payment.datePaid)}`}
          >
            <FiMail aria-hidden="true" />
          </button>
        </span>
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
  // Viewers see the payments and their receipts, but not the ways to change
  // them; members don't see the way to delete them.
  const shown = columns.filter(
    (column) =>
      !(refusal && ["Edit", "Delete"].includes(column.label)) &&
      !(deleteRefusal && column.label === "Delete"),
  );
  return (
    <>
      <DataTable columns={shown} rows={payments} label="Payments" />
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
      <EmailReceiptDialog
        documentId={document._id}
        paymentId={emailingReceipt}
        onClose={() => setEmailingReceipt(null)}
      />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this payment?"
        confirmLabel="Delete payment"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        {removing && <DeletedPayment payment={removing} document={document} />}
      </ConfirmDialog>
    </>
  );
};

export default PaymentsLedger;
