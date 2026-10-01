import { useState } from "react";
import { FiCheck, FiX } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  Field,
  Panel,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  primaryButton,
  rowAction,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useConfirmPaymentClaimMutation,
  useDismissPaymentClaimMutation,
  useGetPaymentClaimsQuery,
} from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";
import { TAX_WITHHELD_HINT, TAX_WITHHELD_LABEL } from "../taxWithheld";

// The method a report's payment is recorded with, unless changed.
const RECORDED_AS = {
  UPI: "UPI",
  BANK_TRANSFER: "Bank Transfer",
  OTHER: "Others",
};

const ConfirmForm = ({ document, claim, onClose }) => {
  const [form, setForm] = useState({
    datePaid: claim.paidOn,
    amountPaid: String(claim.amount),
    paymentMethod: RECORDED_AS[claim.method],
    tdsAmount: "",
    additionalInfo: `Reported by the client${claim.reference ? ` · Ref. ${claim.reference}` : ""}`,
  });
  const [confirm, { isLoading: saving }] = useConfirmPaymentClaimMutation();
  const change = (key) => (event) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await confirm({
        id: document._id,
        claimId: claim.id,
        ...form,
        amountPaid: Number(form.amountPaid),
        tdsAmount: Number(form.tdsAmount) || undefined,
      }).unwrap();
      toast.success(result.message || "The payment was recorded.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The payment could not be recorded."));
    }
  };
  return (
    <form className="mt-3 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        Check it reached you first. It is recorded as below, which you can
        change.
      </p>
      <div className="grid grid-cols-2 gap-5 max-[560px]:grid-cols-1">
        <Field
          label="Date paid"
          type="date"
          required
          value={form.datePaid}
          onChange={change("datePaid")}
          disabled={saving}
        />
        <Field
          label="Amount paid"
          type="number"
          min="0.01"
          step="0.01"
          required
          value={form.amountPaid}
          onChange={change("amountPaid")}
          disabled={saving}
          data-autofocus
        />
        <Field
          label="Payment method"
          as="select"
          required
          value={form.paymentMethod}
          onChange={change("paymentMethod")}
          disabled={saving}
        >
          {PAYMENT_METHODS.map((method) => (
            <option key={method}>{method}</option>
          ))}
        </Field>
        <Field
          label={TAX_WITHHELD_LABEL}
          type="number"
          min="0"
          step="0.01"
          hint={TAX_WITHHELD_HINT}
          value={form.tdsAmount}
          onChange={change("tdsAmount")}
          disabled={saving}
        />
        <Field
          label="Additional info"
          maxLength={255}
          className="col-span-full"
          value={form.additionalInfo}
          onChange={change("additionalInfo")}
          disabled={saving}
        />
      </div>
      <div className="flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={saving}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={saving}>
          <FiCheck aria-hidden="true" />
          {saving ? "Recording…" : "Record payment"}
        </button>
      </div>
    </form>
  );
};

const DismissForm = ({ document, claim, onClose }) => {
  const [reason, setReason] = useState("");
  const [dismiss, { isLoading: saving }] = useDismissPaymentClaimMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await dismiss({
        id: document._id,
        claimId: claim.id,
        reason: reason.trim() || undefined,
      }).unwrap();
      toast.success(result.message || "The payment report was dismissed.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The report could not be dismissed."));
    }
  };
  return (
    <form className="mt-3 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        Nothing is recorded, and the client sees it wasn’t confirmed. The reason
        stays with your team.
      </p>
      <Field
        label="Reason (optional)"
        as="textarea"
        rows={3}
        maxLength={200}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        disabled={saving}
        data-autofocus
      />
      <div className="flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={saving}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={saving}>
          <FiX aria-hidden="true" />
          {saving ? "Dismissing…" : "Dismiss report"}
        </button>
      </div>
    </form>
  );
};

// Payments the client reported from the share page, waiting for someone to
// confirm them as payments or dismiss them. Nothing they owe changes until
// then.
const PaymentClaims = ({ document, refusal }) => {
  const query = useGetPaymentClaimsQuery(document._id);
  const [confirming, setConfirming] = useState(null);
  const [dismissing, setDismissing] = useState(null);
  const waiting = (query.data?.claims || []).filter(
    (claim) => claim.status === "PENDING",
  );
  if (waiting.length === 0) return null;
  return (
    <Panel
      title="Payments to confirm"
      description={`${document.customer?.name || "Your client"} reported paying. Nothing is recorded until you confirm.`}
    >
      <ul className="m-0 flex list-none flex-col p-0">
        {waiting.map((claim) => (
          <li
            key={claim.id}
            className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2.5 border-b border-[#23273512] py-3.5 last:border-b-0"
          >
            <div className="min-w-0 text-[14px] text-spot-ink">
              <Amount value={claim.amount} currency={claim.currency} /> ·{" "}
              {claim.methodLabel}
              {claim.reference ? ` · Ref. ${claim.reference}` : ""}
              <p className={`mt-0.5 ${panelNote}`}>
                Paid {dateLabel(claim.paidOn)}, reported{" "}
                {dateLabel(claim.reportedAt)}
              </p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                className={rowAction}
                onClick={() => setDismissing(claim)}
                disabled={Boolean(refusal)}
                title={refusal}
              >
                <FiX aria-hidden="true" /> Dismiss
              </button>
              <button
                type="button"
                className={rowAction}
                onClick={() => setConfirming(claim)}
                disabled={Boolean(refusal)}
                title={refusal}
              >
                <FiCheck aria-hidden="true" /> Confirm
              </button>
            </div>
          </li>
        ))}
      </ul>
      <Dialog
        open={Boolean(confirming)}
        title="Confirm the payment"
        onClose={() => setConfirming(null)}
        dismissible={false}
        size="medium"
      >
        {confirming && (
          <ConfirmForm
            document={document}
            claim={confirming}
            onClose={() => setConfirming(null)}
          />
        )}
      </Dialog>
      <Dialog
        open={Boolean(dismissing)}
        title="Dismiss the report?"
        onClose={() => setDismissing(null)}
        dismissible={false}
      >
        {dismissing && (
          <DismissForm
            document={document}
            claim={dismissing}
            onClose={() => setDismissing(null)}
          />
        )}
      </Dialog>
    </Panel>
  );
};

export default PaymentClaims;
