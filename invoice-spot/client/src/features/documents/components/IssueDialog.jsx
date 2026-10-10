import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Field,
  dateInput,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  primaryButton,
  retryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useIssueDocMutation } from "../documentsApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import useBusinessToday from "../../business/useBusinessToday";

const DAY = 86400000;

// A day as the date field holds it, some days later.
const daysAfter = (day, days) =>
  dateInput(new Date(`${day}T12:00:00`).getTime() + days * DAY);

const IssueForm = ({
  document,
  terms,
  base,
  gst,
  canEmail,
  emailNote,
  onClose,
  onIssued,
}) => {
  // Today by the business's calendar: the server refuses a document dated
  // after it.
  const today = dateInput(useBusinessToday());
  const [issueDate, setIssueDate] = useState(document.issueDate || today);
  const invoice = document.documentType === "Invoice";
  const quotation = document.documentType === "Quotation";
  // An invoice is due as the payment terms say unless the draft has its own
  // due date; a quotation stays valid as long as the draft says.
  const [dueDate, setDueDate] = useState(
    document.dueDate
      ? dateInput(document.dueDate)
      : invoice
        ? daysAfter(document.issueDate || today, terms)
        : "",
  );
  // A document in another currency than the reporting one can be issued
  // with a rate to it, which is then fixed; in GST mode only a quotation
  // goes out without one. A credit note keeps its invoice's rate.
  const foreign =
    Boolean(document.currency) &&
    document.currency !== base &&
    document.documentType !== "Credit Note";
  const rateNeeded = gst && !quotation;
  const [exchangeRate, setExchangeRate] = useState(
    document.exchangeRate && document.exchangeBase === base
      ? document.exchangeRate
      : "",
  );
  const [email, setEmail] = useState(false);
  const [issue, { isLoading: issuing }] = useIssueDocMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await issue({
        id: document._id,
        issueDate,
        ...(dueDate && { dueDate }),
        ...(foreign && { exchangeRate: exchangeRate.trim() }),
      }).unwrap();
      toast.success(result.message || "The document was issued.");
      onIssued({ email: canEmail && email });
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be issued."));
    }
  };
  return (
    <form className="mt-5 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        It takes the next {document.documentType.toLowerCase()} number, and
        keeps the client’s details and your business’s as they are today.
      </p>
      <div className="grid grid-cols-2 gap-4 max-[560px]:grid-cols-1">
        <Field
          label="Issue date"
          type="date"
          required
          max={today}
          value={issueDate}
          onChange={(event) => setIssueDate(event.target.value)}
          disabled={issuing}
          hint="It can't be later than today."
          data-autofocus
        />
        {(invoice || quotation) && (
          <Field
            label={quotation ? "Valid until" : "Due date"}
            type="date"
            required={invoice}
            min={issueDate}
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
            disabled={issuing}
          />
        )}
        {foreign && (
          <Field
            label={`Exchange rate (1 ${document.currency} in ${base})`}
            type="number"
            min="0.0000000001"
            step="0.0000000001"
            required={rateNeeded}
            value={exchangeRate}
            onChange={(event) => setExchangeRate(event.target.value)}
            disabled={issuing}
            hint={
              rateNeeded
                ? "Needed to issue it in GST mode. It is fixed once issued."
                : `Optional. Reports can then show it in ${base}.`
            }
          />
        )}
      </div>
      <label className="flex items-start gap-3 text-[14px] text-spot-ink">
        <input
          type="checkbox"
          className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
          checked={canEmail && email}
          onChange={(event) => setEmail(event.target.checked)}
          disabled={issuing || !canEmail}
        />
        <span>
          Email it to the client next
          <span className="mt-0.5 block text-[13px] text-spot-muted">
            {canEmail
              ? "You can check the message before it is sent."
              : emailNote}
          </span>
        </span>
      </label>
      <div className="mt-2 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={issuing}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={issuing}>
          <FiCheck aria-hidden="true" />
          {issuing
            ? "Issuing…"
            : `Issue ${document.documentType.toLowerCase()}`}
        </button>
      </div>
    </form>
  );
};

// Issues a draft, dated and due as chosen here, and can go straight on to
// emailing it. The business's payment terms suggest the due date.
const IssueDialog = ({
  document,
  open,
  onClose,
  onIssued,
  canEmail,
  emailNote,
}) => {
  const settings = useGetSettingsQuery(undefined, { skip: !open });
  return (
    <Dialog
      open={open}
      title={`Issue this ${document.documentType.toLowerCase()}`}
      onClose={onClose}
      dismissible={false}
      size="medium"
    >
      {!open ? null : settings.isError ? (
        <div className={`${errorBanner} mt-5`} role="alert">
          <div className="flex-1">
            <strong className={errorTitle}>
              We couldn’t load your payment terms.
            </strong>
            <span className={errorDetail}>{errorMessage(settings.error)}</span>
          </div>
          <button
            type="button"
            className={retryButton}
            onClick={settings.refetch}
          >
            Try again
          </button>
        </div>
      ) : settings.data ? (
        <IssueForm
          document={document}
          terms={settings.data.settings.paymentTermsDays}
          base={settings.data.settings.baseCurrency}
          gst={settings.data.settings.taxRegime === "GST_INDIA"}
          canEmail={canEmail}
          emailNote={emailNote}
          onClose={onClose}
          onIssued={onIssued}
        />
      ) : (
        <p className="mt-4 text-[13.5px] text-spot-muted" role="status">
          Loading…
        </p>
      )}
    </Dialog>
  );
};

export default IssueDialog;
