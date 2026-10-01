import { useState } from "react";
import { FiCheckSquare } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  Badge,
  Field,
  dateInput,
  dateLabel,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";

const METHODS = [
  ["UPI", "UPI"],
  ["BANK_TRANSFER", "Bank transfer"],
  ["OTHER", "Another way"],
];

// How the business answered each report.
const STATUS = {
  PENDING: ["Waiting to be confirmed", "partial"],
  CONFIRMED: ["Confirmed", "paid"],
  DISMISSED: ["Not confirmed", "unpaid"],
};

const ReportForm = ({ doc, report, onClose }) => {
  const balance = Number(doc.balance || 0);
  const [form, setForm] = useState({
    amount: String(balance),
    paidOn: dateInput(),
    method: doc.payment?.upi ? "UPI" : "",
    reference: "",
  });
  const [sending, setSending] = useState(false);
  const change = (key) => (event) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    setSending(true);
    try {
      await report({
        amount: Number(form.amount),
        paidOn: form.paidOn,
        method: form.method,
        reference: form.reference.trim() || undefined,
      });
      toast.success(
        `Thank you. ${doc.business.name} will confirm your payment.`,
      );
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "Your payment could not be reported."));
      setSending(false);
    }
  };
  return (
    <form className="mt-3 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        {doc.business.name} checks it and confirms it. Nothing you owe changes
        until they do.
      </p>
      <div className="grid grid-cols-2 gap-5 max-[560px]:grid-cols-1">
        <Field
          label="Amount paid"
          type="number"
          min="0.01"
          max={balance}
          step="0.01"
          required
          hint={`Up to ${money(balance, doc.currency)} is due.`}
          value={form.amount}
          onChange={change("amount")}
          disabled={sending}
          data-autofocus
        />
        <Field
          label="Day you paid"
          type="date"
          required
          max={dateInput()}
          value={form.paidOn}
          onChange={change("paidOn")}
          disabled={sending}
        />
        <Field
          label="How you paid"
          as="select"
          required
          value={form.method}
          onChange={change("method")}
          disabled={sending}
        >
          <option value="">Choose one</option>
          {METHODS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Field>
        <Field
          label="Reference (optional)"
          maxLength={60}
          hint="Such as the UTR or transaction ID your bank or UPI app shows."
          value={form.reference}
          onChange={change("reference")}
          disabled={sending}
        />
      </div>
      <div className="flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={sending}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={sending}>
          <FiCheckSquare aria-hidden="true" />
          {sending ? "Reporting…" : "Report payment"}
        </button>
      </div>
    </form>
  );
};

// Lets the client of an unpaid invoice say they paid, and shows what they
// reported and whether the business confirmed it. `report` sends a report by
// whichever link the client came.
const ReportPayment = ({ doc, report, heading, line }) => {
  const [open, setOpen] = useState(false);
  const reports = doc.paymentReports || [];
  if (!doc.canReportPayment && reports.length === 0) return null;
  return (
    <section
      className="mt-9 border-t border-[#23273512] pt-7"
      aria-labelledby="reported-payments"
    >
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="min-w-0">
          <h2 id="reported-payments" className={heading}>
            {doc.canReportPayment ? "Paid already?" : "Payments you reported"}
          </h2>
          {doc.canReportPayment && (
            <p className={line}>
              Let {doc.business.name} know, and they’ll confirm it.
            </p>
          )}
        </div>
        {doc.canReportPayment && (
          <button
            type="button"
            className={`${secondaryButton} max-[560px]:w-full`}
            onClick={() => setOpen(true)}
          >
            <FiCheckSquare aria-hidden="true" /> Report a payment
          </button>
        )}
      </div>
      {reports.length > 0 && (
        <ul className="m-0 mt-4 flex list-none flex-col gap-2.5 p-0">
          {reports.map((report) => (
            <li
              key={report.reportedAt}
              className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1.5 rounded-2xl border border-white/90 bg-white/60 px-4 py-3 text-[14px] text-spot-body"
            >
              <span>
                <Amount value={report.amount} currency={doc.currency} /> by{" "}
                {report.method}, paid {dateLabel(report.paidOn)}
              </span>
              <Badge tone={STATUS[report.status]?.[1]}>
                {STATUS[report.status]?.[0] || report.status}
              </Badge>
            </li>
          ))}
        </ul>
      )}
      <Dialog
        open={open}
        title="Report a payment"
        onClose={() => setOpen(false)}
        dismissible={false}
        size="medium"
      >
        {open && (
          <ReportForm
            doc={doc}
            report={report}
            onClose={() => setOpen(false)}
          />
        )}
      </Dialog>
    </section>
  );
};

export default ReportPayment;
