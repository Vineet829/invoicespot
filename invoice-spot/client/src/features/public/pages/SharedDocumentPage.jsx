import { useState } from "react";
import { useParams } from "react-router-dom";
import {
  FiCheckCircle,
  FiClock,
  FiDownload,
  FiLink,
  FiSlash,
  FiXCircle,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useAcceptSharedDocumentMutation,
  useDeclineSharedDocumentMutation,
  useGetSharedDocumentQuery,
  useGetSharedPdfMutation,
} from "../publicApiSlice";
import { saveFile } from "../../documents/useDocumentPdf";
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
  appSurface,
  dangerButton,
  errorBanner,
  errorDetail,
  errorTitle,
  panelNote,
  primaryButton,
  retryButton,
  secondaryButton,
  surface,
} from "../../../components/workspace/pastel";

const frame = `${appSurface} flex min-h-dvh flex-col items-center gap-6 px-8 py-10 max-[700px]:px-4 max-[700px]:py-6`;
const cardBase = `${surface} w-full animate-ov-rise p-9 max-[700px]:rounded-3xl max-[700px]:p-6`;
const card = `${cardBase} max-w-[1000px]`;
const heading = "mb-2 text-[12.5px] font-normal text-spot-muted";
const line =
  "text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]";

const Party = ({ title, party, logo = false }) => (
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

// A message in place of the document, such as for a link that was turned
// off. The link holds no account, so there is nowhere else to send people.
const Notice = ({ title, text, retry }) => (
  <div className={`${cardBase} max-w-[520px]`}>
    <span className="mb-6 grid size-14 place-items-center rounded-[18px] bg-white/80 text-spot-violet shadow-key [&>svg]:size-6 [&>svg]:stroke-[1.5]">
      <FiLink aria-hidden="true" />
    </span>
    <h1 className="font-display text-[32px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink [&>span]:text-spot-brand">
      {title}
      <span>.</span>
    </h1>
    <p className="mt-3 text-[14.5px] leading-[1.6] text-spot-sub">{text}</p>
    {retry && (
      <button type="button" className={`${retryButton} mt-6`} onClick={retry}>
        Try again
      </button>
    )}
  </div>
);

// A document someone shared by link: read only, with its PDF, and for a
// quotation a way to accept it.
const SharedDocumentPage = () => {
  const { token } = useParams();
  const query = useGetSharedDocumentQuery(token);
  const [accept, { isLoading: accepting }] = useAcceptSharedDocumentMutation();
  const [getPdf, { isLoading: downloading }] = useGetSharedPdfMutation();
  const [confirming, setConfirming] = useState(false);
  const [decline, { isLoading: declining }] =
    useDeclineSharedDocumentMutation();
  const [declineOpen, setDeclineOpen] = useState(false);
  const [reason, setReason] = useState("");
  const doc = query.data;
  useTitle(
    doc
      ? `${doc.documentType} ${doc.documentNumber} from ${doc.business.name}`
      : "Shared document — Invoice Spot",
  );
  const quotation = doc?.documentType === "Quotation";
  // A void document keeps its number but is owed no more.
  const voided = doc?.state === "void";
  const download = async () => {
    try {
      const url = await getPdf(token).unwrap();
      saveFile(
        url,
        `${doc.documentNumber.replace(/[^A-Za-z0-9._-]/g, "-")}.pdf`,
      );
    } catch (error) {
      toast.error(errorMessage(error, "The PDF could not be prepared."));
    }
  };
  const confirmDecline = async (event) => {
    event.preventDefault();
    try {
      await decline({ token, reason }).unwrap();
      setDeclineOpen(false);
      toast.success(`Thank you. ${doc.business.name} can now see your answer.`);
    } catch (error) {
      toast.error(errorMessage(error, "The quotation could not be declined."));
    }
  };
  const confirmAccept = async () => {
    try {
      await accept(token).unwrap();
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
    { label: "Item", type: "main", render: (item) => item.itemName },
    { label: "Quantity", render: (item) => item.quantity },
    {
      label: "Unit price",
      type: "amount",
      render: (item) => (
        <Amount value={item.unitPrice} currency={doc.currency} hideCode />
      ),
    },
    { label: "Discount (%)", render: (item) => item.discount || "0" },
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
        [`${doc.taxLabel} (${doc.rates}%)`, doc.salesTax],
        ...(quotation
          ? []
          : [
              ["Total", doc.total],
              ["Paid", doc.received],
            ]),
      ]
    : [];

  return (
    <div className={frame}>
      {query.isLoading ? (
        <p className={panelNote} role="status">
          Opening the document…
        </p>
      ) : query.isError ? (
        query.error?.status === 404 ? (
          <Notice
            title="This link doesn’t work anymore"
            text="It may have been turned off or replaced. Ask the sender for a new link."
          />
        ) : (
          <Notice
            title="We couldn’t open this document"
            text={errorMessage(query.error)}
            retry={query.refetch}
          />
        )
      ) : (
        <main className={card} id="main">
          <header className="flex items-start justify-between gap-6 border-b border-[#23273512] pb-7 max-[700px]:flex-col">
            <Party party={doc.business} logo />
            <div className="flex shrink-0 flex-wrap gap-2.5 max-[700px]:w-full max-[700px]:[&>button]:flex-1">
              <button
                type="button"
                className={secondaryButton}
                onClick={download}
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
                <Badge tone="paid">
                  Accepted on {dateLabel(doc.acceptedAt)}
                </Badge>
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
                !quotation && <StatusBadge status={doc.status} />
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
                  {doc.business.name} voided this{" "}
                  {doc.documentType.toLowerCase()} on {dateLabel(doc.voidedAt)}.
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
            <div>
              <h2 className={heading}>{quotation ? "Valid until" : "Due"}</h2>
              <p className="text-[15px] text-spot-ink">
                {dateLabel(doc.dueDate)}
              </p>
            </div>
          </div>

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
                {quotation ? "Total" : "Balance due"}
              </dt>
              <dd className="text-[24px] text-spot-ink">
                <Amount value={doc.balance} currency={doc.currency} />
              </dd>
            </div>
          </dl>

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
      )}
      <p className="text-[12.5px] text-spot-muted">
        Shared with <span className="text-spot-text">Invoice Spot</span>
      </p>
      {doc && (
        <>
          <Dialog
            open={declineOpen}
            title={`Decline ${doc.documentNumber}?`}
            onClose={() => setDeclineOpen(false)}
            busy={declining}
          >
            <form
              className="mt-3 flex flex-col gap-5"
              onSubmit={confirmDecline}
            >
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
                <button
                  type="submit"
                  className={dangerButton}
                  disabled={declining}
                >
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
      )}
    </div>
  );
};

export default SharedDocumentPage;
