import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  FiBell,
  FiCheck,
  FiCopy,
  FiDownload,
  FiEdit2,
  FiFileMinus,
  FiLink,
  FiMail,
  FiPrinter,
  FiRepeat,
  FiSlash,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useConvertDocMutation,
  useCreditDocMutation,
  useDuplicateDocMutation,
  useGetSingleDocQuery,
} from "../documentsApiSlice";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import { useGetBusinessQuery } from "../../business/businessApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import { RefusalNote, useBusiness } from "../../business/permissions";
import { useDocumentPdf } from "../useDocumentPdf";
import EmailDocumentDialog from "../components/EmailDocumentDialog";
import IssueDialog from "../components/IssueDialog";
import VoidDialog from "../components/VoidDialog";
import EditDetailsDialog from "../components/EditDetailsDialog";
import {
  DocumentStatusBadge,
  documentName,
  isDraft,
  isVoid,
} from "../documentState";
import ActivityTimeline from "../components/ActivityTimeline";
import CreditNotesPanel from "../components/CreditNotesPanel";
import { taxRows } from "../gstRows";
import RefundsPanel from "../components/RefundsPanel";
import ShareDialog from "../components/ShareDialog";
import ReminderDialog from "../components/ReminderDialog";
import ScheduleDialog from "../../recurring/components/ScheduleDialog";
import { useGetDocumentScheduleQuery } from "../../recurring/recurringApiSlice";
import { frequencyLabel } from "../../recurring/frequencies";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Amount,
  LoadingState,
  ErrorState,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  primaryButton,
  secondaryButton,
  secondaryIconButton,
  textLink,
} from "../../../components/workspace/pastel";
import PaymentForm from "./PaymentForm";
import PaymentClaims from "../components/PaymentClaims";
import PaymentsLedger from "../components/PaymentsLedger";
import LineName from "../../gst/LineName";

const DEMO_EMAIL_NOTE = "Emailing is turned off for the shared demo account.";
const partyHeading = "mb-3 text-[13px] font-normal text-spot-muted";
const partyName = "mb-1.5 block text-[15px] font-medium text-spot-ink";
const partyLine =
  "text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]";
const copy =
  "whitespace-pre-wrap text-[14px] leading-[1.7] [overflow-wrap:anywhere]";

const SingleDocumentPage = () => {
  const { id } = useParams();
  const query = useGetSingleDocQuery(id);
  const profileQuery = useGetUserProfileQuery();
  const document = query.data?.document;
  const profile = profileQuery.data?.userProfile;
  const business = useGetBusinessQuery().data?.business;
  // The business as the document went out with it, whatever has changed since.
  // A draft keeps nothing yet, so it shows the business and its tax name as
  // they are now.
  const seller = document?.seller || business;
  const liveSettings = useGetSettingsQuery(undefined, {
    skip: !isDraft(document),
  }).data?.settings;
  const taxLabel = document?.taxLabel || liveSettings?.taxLabel || "Tax";
  const { reason } = useBusiness();
  const editRefusal = reason("EDIT_RECORDS");
  const voidRefusal = reason("DELETE_RECORDS");
  // Sending is refused to viewers, and turned off for the demo account.
  const sendNote = editRefusal || (profile?.demo ? DEMO_EMAIL_NOTE : undefined);
  const sendNoteId = editRefusal ? "document-edit-refusal" : "demo-email-note";
  // Shown only when it differs from the name the business trades under.
  const legalName =
    seller?.legalName &&
    seller.legalName.trim().toLowerCase() !== seller.name?.trim().toLowerCase()
      ? seller.legalName
      : null;
  const draft = isDraft(document);
  // A void document stays to be read, downloaded or copied, and nothing more.
  const voided = isVoid(document);
  const [issuing, setIssuing] = useState(false);
  const [voiding, setVoiding] = useState(false);
  const [editingDetails, setEditingDetails] = useState(false);
  // A draft is edited in full; an issued document only has its due date,
  // notes and terms changed, and an accepted quotation nothing.
  const detailsLocked = voided || Boolean(document?.acceptedAt);
  const [emailing, setEmailing] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [reminding, setReminding] = useState(false);
  const [scheduling, setScheduling] = useState(false);
  const scheduleQuery = useGetDocumentScheduleQuery(id, {
    skip: query.data?.document?.documentType !== "Invoice",
  });
  const schedule = scheduleQuery.data?.schedule;
  const pdf = useDocumentPdf(document);
  const navigate = useNavigate();
  const [duplicateDoc, { isLoading: duplicating }] = useDuplicateDocMutation();
  const [convertDoc, { isLoading: converting }] = useConvertDocMutation();
  const [creditDoc, { isLoading: crediting }] = useCreditDocMutation();
  // A credit note takes an amount off an issued invoice or receipt; it has
  // no payments, due date or copies of its own.
  const creditNote = document?.documentType === "Credit Note";
  const creditable =
    ["Invoice", "Receipt"].includes(document?.documentType) &&
    !draft &&
    !voided &&
    Number(document?.ledger?.creditable) > 0;
  useTitle(`${documentName(document)} — Invoice Spot`);
  // A quotation turned into an invoice lists it as "Invoiced as"; any other
  // copy as "Copied to".
  const copies = document?.copies || [];
  const invoicedAs = copies.filter(
    (link) =>
      document.documentType === "Quotation" && link.documentType === "Invoice",
  );
  const copiedTo = copies.filter((link) => !invoicedAs.includes(link));
  const duplicate = async () => {
    try {
      const { newDocument } = await duplicateDoc(id).unwrap();
      toast.success("Copied as a draft. Review it, then issue it.");
      navigate(`/edit-doc/${newDocument._id}`);
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be copied."));
    }
  };
  const credit = async () => {
    try {
      const { newDocument } = await creditDoc(id).unwrap();
      toast.success(
        `A draft credit note was made for ${document.documentNumber}. Set what it credits, then issue it.`,
      );
      navigate(`/edit-doc/${newDocument._id}`);
    } catch (error) {
      toast.error(errorMessage(error, "The credit note could not be made."));
    }
  };
  const convert = async () => {
    try {
      const { newDocument } = await convertDoc(id).unwrap();
      toast.success(
        `A draft invoice was made from ${document.documentNumber}. Review it, then issue it.`,
      );
      navigate(`/document/${newDocument._id}`);
    } catch (error) {
      toast.error(
        errorMessage(error, "The quotation could not become an invoice."),
      );
    }
  };
  const totalAmountReceived = Number(document?.totalAmountReceived || 0);
  const columns = [
    {
      label: "#",
      type: "index",
      render: (_item, index) => index + 1,
    },
    {
      label: "Product / service",
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
        <Amount value={item.unitPrice} currency={document?.currency} hideCode />
      ),
    },
    { label: "Discount (%)", render: (item) => item.discount || "0" },
    // Each line's rate shows when the lines are taxed at more than one.
    ...(document?.taxes?.length > 1
      ? [{ label: `${taxLabel} %`, render: (item) => item.taxRate }]
      : []),
    {
      label: "Line total",
      type: "amount",
      render: (item) => (
        <Amount value={item.lineTotal} currency={document?.currency} />
      ),
    },
  ];
  return (
    <WorkspacePage section="Documents">
      <PageHeader
        back
        title={document ? documentName(document) : "Document"}
        description={
          document
            ? `${document.documentType} for ${document.customer?.name || "your customer"}`
            : "Your document details."
        }
        actions={
          document && (
            <>
              {draft ? (
                <ActionLink
                  to={`/edit-doc/${id}`}
                  secondary
                  reason={editRefusal}
                >
                  <FiEdit2 aria-hidden="true" /> Edit document
                </ActionLink>
              ) : (
                !detailsLocked && (
                  <button
                    type="button"
                    className={secondaryButton}
                    onClick={() => setEditingDetails(true)}
                    disabled={Boolean(editRefusal)}
                    title={editRefusal}
                    aria-describedby={
                      editRefusal ? "document-edit-refusal" : undefined
                    }
                  >
                    <FiEdit2 aria-hidden="true" /> Edit details
                  </button>
                )
              )}
              <button
                type="button"
                className={secondaryIconButton}
                onClick={pdf.download}
                disabled={Boolean(pdf.busy)}
                aria-busy={pdf.busy === "download"}
                aria-label="Download PDF"
                title="Download PDF"
              >
                <FiDownload aria-hidden="true" />
              </button>
              <button
                type="button"
                className={secondaryIconButton}
                onClick={pdf.print}
                disabled={Boolean(pdf.busy)}
                aria-busy={pdf.busy === "print"}
                aria-label="Print"
                title="Print"
              >
                <FiPrinter aria-hidden="true" />
              </button>
              {!creditNote && (
                <button
                  type="button"
                  className={secondaryIconButton}
                  onClick={duplicate}
                  disabled={duplicating || Boolean(editRefusal)}
                  aria-busy={duplicating}
                  aria-label="Duplicate"
                  title={editRefusal || "Duplicate"}
                  aria-describedby={
                    editRefusal ? "document-edit-refusal" : undefined
                  }
                >
                  <FiCopy aria-hidden="true" />
                </button>
              )}
              {!draft && !voided && (
                <button
                  type="button"
                  className={secondaryIconButton}
                  onClick={() => setSharing(true)}
                  disabled={Boolean(editRefusal)}
                  aria-label="Share link"
                  title={editRefusal || "Share link"}
                  aria-describedby={
                    editRefusal ? "document-edit-refusal" : undefined
                  }
                >
                  <FiLink aria-hidden="true" />
                </button>
              )}
              {!draft && !voided && (
                <button
                  type="button"
                  className={secondaryIconButton}
                  onClick={() => setVoiding(true)}
                  disabled={Boolean(voidRefusal)}
                  aria-label="Void"
                  title={voidRefusal || "Void"}
                  aria-describedby={
                    voidRefusal ? "document-void-refusal" : undefined
                  }
                >
                  <FiSlash aria-hidden="true" />
                </button>
              )}
              {document.documentType === "Quotation" &&
                !draft &&
                !voided &&
                !invoicedAs.length && (
                  <button
                    type="button"
                    className={secondaryButton}
                    onClick={convert}
                    disabled={converting || Boolean(editRefusal)}
                    aria-busy={converting}
                    title={editRefusal}
                    aria-describedby={
                      editRefusal ? "document-edit-refusal" : undefined
                    }
                  >
                    <FiRepeat aria-hidden="true" />
                    {converting ? "Converting…" : "Convert to invoice"}
                  </button>
                )}
              {creditable && (
                <button
                  type="button"
                  className={secondaryButton}
                  onClick={credit}
                  disabled={crediting || Boolean(editRefusal)}
                  aria-busy={crediting}
                  title={editRefusal}
                  aria-describedby={
                    editRefusal ? "document-edit-refusal" : undefined
                  }
                >
                  <FiFileMinus aria-hidden="true" />
                  {crediting ? "Making…" : "Credit note"}
                </button>
              )}
              {document.documentType === "Invoice" &&
                !draft &&
                !voided &&
                document.status !== "Paid" && (
                  <button
                    type="button"
                    className={secondaryButton}
                    onClick={() => setReminding(true)}
                    disabled={Boolean(sendNote)}
                    title={sendNote}
                    aria-describedby={sendNote ? sendNoteId : undefined}
                  >
                    <FiBell aria-hidden="true" /> Send reminder
                  </button>
                )}
              {draft ? (
                <button
                  type="button"
                  className={primaryButton}
                  onClick={() => setIssuing(true)}
                  disabled={Boolean(editRefusal)}
                  title={editRefusal}
                  aria-describedby={
                    editRefusal ? "document-edit-refusal" : undefined
                  }
                >
                  <FiCheck aria-hidden="true" /> Issue{" "}
                  {document.documentType.toLowerCase()}
                </button>
              ) : (
                !voided && (
                  <button
                    type="button"
                    className={primaryButton}
                    onClick={() => setEmailing(true)}
                    disabled={Boolean(sendNote)}
                    title={sendNote}
                    aria-describedby={sendNote ? sendNoteId : undefined}
                  >
                    <FiMail aria-hidden="true" /> Email document
                  </button>
                )
              )}
              {profile?.demo && (
                <span id="demo-email-note" className="sr-only">
                  {DEMO_EMAIL_NOTE}
                </span>
              )}
              <RefusalNote id="document-edit-refusal" reason={editRefusal} />
              <RefusalNote id="document-void-refusal" reason={voidRefusal} />
            </>
          )
        }
      />
      {query.isLoading || profileQuery.isLoading ? (
        <LoadingState />
      ) : query.isError || profileQuery.isError ? (
        <ErrorState
          error={query.error || profileQuery.error}
          retry={() => {
            query.refetch();
            profileQuery.refetch();
          }}
        />
      ) : (
        document && (
          <>
            {voided && (
              <p className={errorBanner} role="note">
                <FiSlash aria-hidden="true" />
                <span>
                  <strong className={errorTitle}>
                    Voided on {dateLabel(document.voidedAt)}. It no longer
                    counts toward anything owed.
                  </strong>
                  <span className={errorDetail}>{document.voidReason}</span>
                </span>
              </p>
            )}
            <Panel
              title={document.documentType}
              aside={
                <span className="flex flex-wrap justify-end gap-1.5">
                  <DocumentStatusBadge document={document} />
                </span>
              }
            >
              <div className="grid grid-cols-3 gap-8 max-[960px]:grid-cols-2 max-[700px]:grid-cols-1 max-[700px]:gap-6">
                <div className="min-w-0">
                  <h3 className={partyHeading}>From</h3>
                  {seller?.logo && (
                    <img
                      className="mb-3 max-h-14 max-w-[120px] object-contain"
                      src={seller.logo}
                      alt={`${seller.name} logo`}
                    />
                  )}
                  <strong className={partyName}>{seller?.name}</strong>
                  {[
                    legalName,
                    seller?.email,
                    seller?.phoneNumber,
                    seller?.address,
                    seller?.city,
                    [seller?.state, seller?.postalCode]
                      .filter(Boolean)
                      .join(" "),
                    seller?.country,
                    // Under GST the tax ID is the GSTIN, and the state it is
                    // registered in follows, as on the PDF.
                    seller?.taxId &&
                      `${document.gst?.supplyType ? "GSTIN" : "Tax ID"} ${seller.taxId}`,
                    document.gst?.supplyType &&
                      document.gst.sellerStateCode &&
                      `State: ${document.gst.sellerStateName} (${document.gst.sellerStateCode})`,
                  ]
                    .filter(Boolean)
                    .map((value, index) => (
                      <p className={partyLine} key={index}>
                        {value}
                      </p>
                    ))}
                </div>
                <div className="min-w-0">
                  <h3 className={partyHeading}>For</h3>
                  <strong className={partyName}>
                    {document.customerId ? (
                      <Link
                        className={textLink}
                        to={`/single-customer/${document.customerId}`}
                      >
                        {document.customer?.name}
                      </Link>
                    ) : (
                      document.customer?.name
                    )}
                  </strong>
                  <p className={partyLine}>
                    Customer no. {document.customer?.accountNo || "—"}
                  </p>
                  <p className={partyLine}>
                    VAT / TIN: {document.customer?.vatTinNo || "—"}
                  </p>
                  {document.customer?.gstin && (
                    <p className={partyLine}>
                      GSTIN: {document.customer.gstin}
                    </p>
                  )}
                  {[
                    document.customer?.email,
                    document.customer?.phoneNumber,
                    document.customer?.address,
                    document.customer?.city,
                    document.customer?.country,
                  ]
                    .filter(Boolean)
                    .map((value, index) => (
                      <p className={partyLine} key={index}>
                        {value}
                      </p>
                    ))}
                </div>
                <div className="min-w-0 max-[960px]:col-span-full max-[700px]:col-span-1">
                  <h3 className={partyHeading}>Document details</h3>
                  <dl className="grid gap-4 max-[960px]:grid-cols-2 max-[700px]:grid-cols-1">
                    {[
                      [
                        "Issue date",
                        draft && !document.issueDate
                          ? "On issue"
                          : dateLabel(document.issueDate),
                      ],
                      ...(creditNote
                        ? [
                            [
                              "Credits",
                              <Link
                                className={textLink}
                                to={`/document/${document.creditedDocument._id}`}
                              >
                                {documentName(document.creditedDocument)}
                              </Link>,
                            ],
                            ...(document.creditReason
                              ? [["Reason", document.creditReason]]
                              : []),
                          ]
                        : [
                            [
                              document.documentType === "Quotation"
                                ? "Valid until"
                                : "Due date",
                              dateLabel(document.dueDate),
                            ],
                          ]),
                      ...(document.gst?.placeOfSupply
                        ? [
                            [
                              "Place of supply",
                              `${document.gst.placeOfSupply} – ${document.gst.placeOfSupplyName}`,
                            ],
                            ...(document.gst.reverseCharge
                              ? [["Reverse charge", "Yes, the recipient pays"]]
                              : []),
                            ...(document.gst.supplyType === "EXPORT_LUT"
                              ? [
                                  [
                                    "Export",
                                    `Under LUT ${document.gst.lutReference}`,
                                  ],
                                ]
                              : document.gst.supplyType === "EXPORT_IGST"
                                ? [["Export", "With IGST"]]
                                : []),
                          ]
                        : []),
                      ...(document.exchangeRate
                        ? [
                            [
                              "Exchange rate",
                              `1 ${document.currency} = ${document.exchangeRate} ${document.exchangeBase}`,
                            ],
                          ]
                        : []),
                      ...(document.acceptedAt
                        ? [["Accepted on", dateLabel(document.acceptedAt)]]
                        : []),
                      ...(document.declinedAt
                        ? [
                            ["Declined on", dateLabel(document.declinedAt)],
                            ...(document.declineReason
                              ? [["Their reason", document.declineReason]]
                              : []),
                          ]
                        : []),
                    ].map(([label, value]) => (
                      <div key={label}>
                        <dt className={partyLine}>{label}</dt>
                        <dd className="text-[15px] text-spot-ink">{value}</dd>
                      </div>
                    ))}
                    {[
                      [
                        document.source?.documentType === "Quotation" &&
                        document.documentType === "Invoice"
                          ? "Converted from"
                          : "Copied from",
                        document.source ? [document.source] : [],
                      ],
                      ["Invoiced as", invoicedAs],
                      ["Copied to", copiedTo],
                    ]
                      .filter(([, links]) => links.length)
                      .map(([label, links]) => (
                        <div key={label}>
                          <dt className={partyLine}>{label}</dt>
                          <dd className="flex flex-wrap gap-x-3 text-[15px]">
                            {links.map((link) => (
                              <Link
                                key={link._id}
                                className={textLink}
                                to={`/document/${link._id}`}
                              >
                                {documentName(link)}
                              </Link>
                            ))}
                          </dd>
                        </div>
                      ))}
                    {document.documentType === "Invoice" &&
                      !voided &&
                      scheduleQuery.isSuccess && (
                        <div>
                          <dt className={partyLine}>Repeats</dt>
                          <dd className="flex flex-wrap items-baseline gap-x-3 text-[15px] text-spot-ink">
                            {schedule ? (
                              <span>
                                {frequencyLabel(schedule.frequency)}
                                {schedule.finished
                                  ? ", finished"
                                  : schedule.paused
                                    ? ", paused"
                                    : `, next ${dateLabel(schedule.nextRun)}`}
                              </span>
                            ) : (
                              <span className="text-spot-muted">No</span>
                            )}
                            {!editRefusal && (
                              <button
                                type="button"
                                className={`${textLink} bg-transparent p-0 text-[13.5px]`}
                                onClick={() => setScheduling(true)}
                              >
                                {schedule ? "Change" : "Make recurring"}
                              </button>
                            )}
                          </dd>
                        </div>
                      )}
                    {document.remindersSent > 0 && (
                      <div>
                        <dt className={partyLine}>Reminders</dt>
                        <dd className="text-[15px] text-spot-ink">
                          {document.remindersSent} sent, last on{" "}
                          {dateLabel(document.lastRemindedAt)}
                        </dd>
                      </div>
                    )}
                  </dl>
                </div>
              </div>
            </Panel>
            <Panel title="Order details">
              <DataTable
                columns={columns}
                rows={document.billingItems || []}
                rowKey={(_row, index) => index}
                label="Order details"
              />
            </Panel>
            <div className="grid grid-cols-2 items-start gap-6 compact:gap-5 max-[1100px]:grid-cols-1">
              <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
                <Panel title="Additional info">
                  <p
                    className={`${copy} ${document.additionalInfo ? "text-spot-body" : "text-spot-faint"}`}
                  >
                    {document.additionalInfo || "No additional info."}
                  </p>
                </Panel>
                <Panel title="Terms & conditions">
                  <p
                    className={`${copy} ${document.termsConditions ? "text-spot-body" : "text-spot-faint"}`}
                  >
                    {document.termsConditions || "No terms provided."}
                  </p>
                </Panel>
              </div>
              <Panel title="Cost summary">
                <dl className="m-0">
                  {[
                    ["Subtotal", document.subTotal],
                    ...taxRows(document.taxes || [], document.gst, taxLabel),
                    ...(creditNote
                      ? [["Total credit", document.total]]
                      : [
                          ["Cumulative total", document.total],
                          ["Amount paid", totalAmountReceived],
                          ...(Number(document.ledger?.totalTds) > 0
                            ? [["Tax withheld", document.ledger.totalTds]]
                            : []),
                          ...(Number(document.ledger?.totalCredited) > 0
                            ? [["Credited", document.ledger.totalCredited]]
                            : []),
                          ...(Number(document.ledger?.totalRefunded) > 0
                            ? [["Refunded", document.ledger.totalRefunded]]
                            : []),
                          !voided && Number(document.ledger?.refundDue) > 0
                            ? ["Refund due", document.ledger.refundDue]
                            : [
                                "Balance",
                                voided
                                  ? 0
                                  : Number(document.ledger?.balanceDue || 0),
                              ],
                        ]),
                  ].map(([label, value], index, rows) => {
                    const balance = index === rows.length - 1;
                    return (
                      <div
                        key={label}
                        className={`flex items-baseline justify-between gap-5 ${balance ? "mt-2 border-t border-[#23273514] pt-5" : "py-2.5"}`}
                      >
                        <dt className="text-[13.5px] text-spot-body">
                          {label}
                        </dt>
                        <dd className="text-right text-spot-ink [overflow-wrap:anywhere]">
                          <Amount
                            value={value}
                            currency={document.currency}
                            className={
                              balance
                                ? "text-[34px] font-extralight tracking-[-0.05em]"
                                : "text-[17px] font-light tracking-[-0.03em]"
                            }
                          />
                        </dd>
                      </div>
                    );
                  })}
                </dl>
              </Panel>
            </div>
            {document.paymentRecords?.length > 0 && (
              <Panel
                title="Payments"
                description="Payments recorded against this document."
              >
                <PaymentsLedger
                  document={document}
                  demo={profile?.demo}
                  refusal={editRefusal}
                />
              </Panel>
            )}
            {document.creditNotes?.length > 0 && (
              <CreditNotesPanel document={document} />
            )}
            {!voided &&
              (Number(document.ledger?.refundDue) > 0 ||
                document.refunds?.length > 0) && (
                <RefundsPanel document={document} refusal={editRefusal} />
              )}
            {document.documentType === "Invoice" && !draft && !voided && (
              <PaymentClaims document={document} refusal={editRefusal} />
            )}
            {document.documentType === "Invoice" &&
              !draft &&
              !voided &&
              document.status !== "Paid" &&
              !editRefusal && (
                <Panel
                  title="Record payment"
                  description={`Record a payment from ${document.customer?.name || "your customer"}.`}
                >
                  <PaymentForm document={document} />
                </Panel>
              )}
            <Panel
              title="Activity"
              description="Everything that happened to this document."
            >
              <ActivityTimeline documentId={id} />
            </Panel>
          </>
        )
      )}
      <EmailDocumentDialog
        documentId={id}
        open={emailing}
        onClose={() => setEmailing(false)}
      />
      {document && !draft && !detailsLocked && (
        <EditDetailsDialog
          document={document}
          open={editingDetails}
          onClose={() => setEditingDetails(false)}
        />
      )}
      {document && !draft && !voided && (
        <VoidDialog
          document={document}
          open={voiding}
          onClose={() => setVoiding(false)}
        />
      )}
      {document && (
        <IssueDialog
          document={document}
          open={issuing}
          onClose={() => setIssuing(false)}
          onIssued={({ email }) => {
            setIssuing(false);
            if (email) setEmailing(true);
          }}
          canEmail={!sendNote}
          emailNote={sendNote}
        />
      )}
      <ReminderDialog
        documentId={id}
        open={reminding}
        onClose={() => setReminding(false)}
      />
      <ScheduleDialog
        open={scheduling}
        onClose={() => setScheduling(false)}
        documentId={id}
        documentNumber={document?.documentNumber}
        schedule={schedule}
        demo={profile?.demo}
      />
      {document && (
        <ShareDialog
          document={document}
          open={sharing}
          onClose={() => setSharing(false)}
        />
      )}
    </WorkspacePage>
  );
};
export default SingleDocumentPage;
