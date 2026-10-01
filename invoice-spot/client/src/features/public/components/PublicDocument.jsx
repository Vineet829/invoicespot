import { useState } from "react";
import {
  FiCheckCircle,
  FiClock,
  FiDownload,
  FiSlash,
  FiXCircle,
} from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Amount,
  Badge,
  DataTable,
  Field,
  StatusBadge,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  dangerButton,
  errorBanner,
  errorDetail,
  errorTitle,
  primaryButton,
  secondaryButton,
  surface,
} from "../../../components/workspace/pastel";
import { taxRows } from "../../documents/gstRows";
import LineName from "../../gst/LineName";
import PaymentDetails from "./PaymentDetails";
import ReportPayment from "./ReportPayment";

export const cardBase = `${surface} w-full animate-ov-rise p-9 max-[700px]:rounded-3xl max-[700px]:p-6`;
export const card = `${cardBase} max-w-[1000px]`;
const heading = "mb-2 text-[12.5px] font-normal text-spot-muted";
const line =
  "text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]";

export const Party = ({ title, party, logo = false }) => (
  <div className="min-w-0">
    {title && <h2 className={heading}>{title}</h2>}
    {logo && party.logo && (
      <img
        className="mb-3 max-h-14 max-w-[140px] object-contain"
        src={party.logo}
        alt={`${party.name} logo`}
      />
    )}
    <strong className="mb-1 block text-[15px] font-medium text-spot-ink [overflow-wrap:anywhere]">
      {party.name}
    </strong>
    {party.lines.map((value) => (
      <p className={line} key={value}>
        {value}
      </p>
    ))}
  </div>
);

// A document as its client sees it, read only: its PDF, for an unpaid invoice
// how to pay it and a way to say it was paid, and for a quotation a way to
// accept or decline it. The page around it says which link it came by, and
// passes what each action does there.
const PublicDocument = ({
  doc,
  onDownload,
  downloading,
  accept,
  accepting,
  decline,
  declining,
  report,
}) => {
  const [confirming, setConfirming] = useState(false);
  const [declineOpen, setDeclineOpen] = useState(false);
  const [reason, setReason] = useState("");
  const quotation = doc?.documentType === "Quotation";
  // A credit note takes an amount off another document; nothing is paid on it.
  const creditNote = doc?.documentType === "Credit Note";
  // Paid beyond what is owed once credits are taken off.
  const refundDue = Number(doc?.refundDue) > 0;
  // A void document keeps its number but is owed no more.
  const voided = doc?.state === "void";
  const confirmDecline = async (event) => {
    event.preventDefault();
    try {
      await decline(reason);
      setDeclineOpen(false);
      toast.success(`Thank you. ${doc.business.name} can now see your answer.`);
    } catch (error) {
      toast.error(errorMessage(error, "The quotation could not be declined."));
    }
  };
  const confirmAccept = async () => {
    try {
      await accept();
      setConfirming(false);
      toast.success(
        `Thank you. ${doc.business.name} can now see your acceptance.`,
      );
    } catch (error) {
      toast.error(errorMessage(error, "The quotation could not be accepted."));
    }
  };
  const columns = [
    { label: "#", type: "index", render: (_item, index) => index + 1 },
    {
      label: "Item",
      type: "main",
      render: (item) => <LineName name={item.itemName} hsnSac={item.hsnSac} />,
    },
    {
      label: "Quantity",
      render: (item) =>
        item.unit ? `${item.quantity} ${item.unit}` : item.quantity,
    },
    {
      label: "Unit price",
      type: "amount",
      render: (item) => (
        <Amount value={item.unitPrice} currency={doc.currency} hideCode />
      ),
    },
    { label: "Discount (%)", render: (item) => item.discount || "0" },
    // Each line's rate shows when the lines are taxed at more than one.
    ...(doc?.taxes?.length > 1
      ? [{ label: `${doc.taxLabel} %`, render: (item) => item.taxRate }]
      : []),
    {
      label: "Amount",
      type: "amount",
      render: (item) => (
        <Amount value={item.lineTotal} currency={doc.currency} />
      ),
    },
  ];
  const totals = doc
    ? [
        ["Subtotal", doc.subTotal],
        ...taxRows(doc.taxes, doc.gst, doc.taxLabel),
        ...(quotation || creditNote
          ? []
          : [
              ["Total", doc.total],
              ["Paid", doc.received],
              ...(Number(doc.taxWithheld) > 0
                ? [["Tax withheld", doc.taxWithheld]]
                : []),
              ...(Number(doc.credited) > 0 ? [["Credited", doc.credited]] : []),
              ...(Number(doc.refunded) > 0
                ? [["Refunded to you", doc.refunded]]
                : []),
            ]),
      ]
    : [];

  return (
    <>
      <main className={card} id="main">
        <header className="flex items-start justify-between gap-6 border-b border-[#23273512] pb-7 max-[700px]:flex-col">
          <Party party={doc.business} logo />
          <div className="flex shrink-0 flex-wrap gap-2.5 max-[700px]:w-full max-[700px]:[&>button]:flex-1">
            <button
              type="button"
              className={secondaryButton}
              onClick={onDownload}
              disabled={downloading}
              aria-busy={downloading}
            >
              <FiDownload aria-hidden="true" />
              {downloading ? "Preparing…" : "Download PDF"}
            </button>
            {doc.canDecline && (
              <button
                type="button"
                className={secondaryButton}
                onClick={() => setDeclineOpen(true)}
              >
                <FiXCircle aria-hidden="true" /> Decline
              </button>
            )}
            {doc.canAccept && (
              <button
                type="button"
                className={primaryButton}
                onClick={() => setConfirming(true)}
              >
                <FiCheckCircle aria-hidden="true" /> Accept quotation
              </button>
            )}
          </div>
        </header>

        <div className="mt-7 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <p className="text-[13px] text-spot-muted">{doc.documentType}</p>
            <h1 className="font-display text-[40px] font-extralight leading-[1.1] tracking-[-0.05em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[31px] [&>span]:text-spot-brand">
              {doc.documentNumber}
              <span>.</span>
            </h1>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {doc.acceptedAt && (
              <Badge tone="paid">Accepted on {dateLabel(doc.acceptedAt)}</Badge>
            )}
            {doc.declinedAt && (
              <Badge tone="unpaid">
                Declined on {dateLabel(doc.declinedAt)}
              </Badge>
            )}
            {doc.expired && <Badge tone="partial">Expired</Badge>}
            {voided ? (
              <Badge tone="unpaid">Void</Badge>
            ) : (
              !quotation && !creditNote && <StatusBadge status={doc.status} />
            )}
            {doc.overdue && <Badge tone="unpaid">Overdue</Badge>}
          </div>
        </div>

        {doc.expired && (
          <p
            className="mt-6 flex items-start gap-3.5 rounded-xl border border-white/90 bg-white/60 px-5 py-4 text-[14px] leading-[1.6] text-spot-body [&>svg]:mt-0.5 [&>svg]:size-5 [&>svg]:shrink-0"
            role="note"
          >
            <FiClock aria-hidden="true" />
            <span>
              This quotation expired on {dateLabel(doc.dueDate)}. Ask{" "}
              {doc.business.name} for a new one.
            </span>
          </p>
        )}
        {voided && (
          <p className={`${errorBanner} mt-6`} role="note">
            <FiSlash aria-hidden="true" />
            <span>
              <strong className={errorTitle}>
                {doc.business.name} voided this {doc.documentType.toLowerCase()}{" "}
                on {dateLabel(doc.voidedAt)}.
              </strong>
              <span className={errorDetail}>{doc.voidReason}</span>
            </span>
          </p>
        )}

        <div className="mt-7 grid grid-cols-3 gap-8 max-[860px]:grid-cols-2 max-[560px]:grid-cols-1">
          <Party title="For" party={doc.client} />
          <div>
            <h2 className={heading}>Issued</h2>
            <p className="text-[15px] text-spot-ink">
              {dateLabel(doc.issueDate)}
            </p>
          </div>
          {creditNote ? (
            <div>
              <h2 className={heading}>Credits</h2>
              <p className="text-[15px] text-spot-ink">
                {doc.credits.documentType} {doc.credits.documentNumber}
              </p>
              {doc.credits.issueDate && (
                <p className="text-[13.5px] text-spot-body">
                  Issued {dateLabel(doc.credits.issueDate)}
                </p>
              )}
            </div>
          ) : (
            <div>
              <h2 className={heading}>{quotation ? "Valid until" : "Due"}</h2>
              <p className="text-[15px] text-spot-ink">
                {dateLabel(doc.dueDate)}
              </p>
            </div>
          )}
        </div>
        {doc.gst?.placeOfSupply && (
          <p className="mt-6 text-[13.5px] text-spot-body">
            Place of supply: {doc.gst.placeOfSupply} –{" "}
            {doc.gst.placeOfSupplyName}
            {doc.gst.reverseCharge && " · Reverse charge: you pay the GST"}
          </p>
        )}
        {doc.creditReason && (
          <div className="mt-7">
            <h2 className={heading}>Reason for this credit</h2>
            <p className="whitespace-pre-wrap text-[14px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]">
              {doc.creditReason}
            </p>
          </div>
        )}

        <section className="mt-9" aria-label="Items">
          <DataTable
            columns={columns}
            rows={doc.items}
            rowKey={(_item, index) => index}
            label="Items"
          />
        </section>

        <dl className="ml-auto mt-6 w-full max-w-[360px]">
          {totals.map(([label, value]) => (
            <div
              key={label}
              className="flex items-baseline justify-between gap-6 py-1.5 text-[14px]"
            >
              <dt className="text-spot-body">{label}</dt>
              <dd>
                <Amount value={value} currency={doc.currency} />
              </dd>
            </div>
          ))}
          <div className="mt-2 flex items-baseline justify-between gap-6 border-t border-[#23273517] pt-3.5">
            <dt className="text-[15px] font-medium text-spot-ink">
              {quotation
                ? "Total"
                : creditNote
                  ? "Total credit"
                  : refundDue
                    ? "Due back to you"
                    : "Balance due"}
            </dt>
            <dd className="text-[24px] text-spot-ink">
              <Amount
                value={refundDue ? doc.refundDue : doc.balance}
                currency={doc.currency}
              />
            </dd>
          </div>
        </dl>

        {doc.payment && <PaymentDetails payment={doc.payment} />}
        <ReportPayment
          doc={doc}
          report={report}
          heading={heading}
          line={line}
        />

        {(doc.notes || doc.terms) && (
          <div className="mt-9 grid grid-cols-2 gap-8 border-t border-[#23273512] pt-7 max-[700px]:grid-cols-1">
            {[
              ["Notes", doc.notes],
              ["Terms & conditions", doc.terms],
            ]
              .filter(([, text]) => text)
              .map(([label, text]) => (
                <div key={label}>
                  <h2 className={heading}>{label}</h2>
                  <p className={`${line} whitespace-pre-wrap`}>{text}</p>
                </div>
              ))}
          </div>
        )}
      </main>
      <Dialog
        open={declineOpen}
        title={`Decline ${doc.documentNumber}?`}
        onClose={() => setDeclineOpen(false)}
        busy={declining}
      >
        <form className="mt-3 flex flex-col gap-5" onSubmit={confirmDecline}>
          <p className="text-[14px] leading-[1.6] text-spot-body">
            {doc.business.name} can see that you declined it, and why if you
            say.
          </p>
          <Field
            label="Reason (optional)"
            as="textarea"
            rows={3}
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            disabled={declining}
            data-autofocus
          />
          <div className="flex flex-wrap justify-end gap-2.5">
            <button
              type="button"
              className={secondaryButton}
              onClick={() => setDeclineOpen(false)}
              disabled={declining}
            >
              Cancel
            </button>
            <button type="submit" className={dangerButton} disabled={declining}>
              <FiXCircle aria-hidden="true" />
              {declining ? "Declining…" : "Decline quotation"}
            </button>
          </div>
        </form>
      </Dialog>
      <Dialog
        open={confirming}
        title={`Accept ${doc.documentNumber}?`}
        onClose={() => setConfirming(false)}
        busy={accepting}
      >
        <p className="mt-3 text-[14px] leading-[1.6] text-spot-body">
          You agree to the items, prices and terms in this quotation, and{" "}
          {doc.business.name} can see that you accepted it.
        </p>
        <div className="mt-7 flex flex-wrap justify-end gap-2.5">
          <button
            type="button"
            className={secondaryButton}
            onClick={() => setConfirming(false)}
            disabled={accepting}
            data-autofocus
          >
            Cancel
          </button>
          <button
            type="button"
            className={primaryButton}
            onClick={confirmAccept}
            disabled={accepting}
          >
            <FiCheckCircle aria-hidden="true" />
            {accepting ? "Accepting…" : "Accept quotation"}
          </button>
        </div>
      </Dialog>
    </>
  );
};

export default PublicDocument;
