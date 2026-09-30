import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  FiBell,
  FiCopy,
  FiDownload,
  FiEdit2,
  FiLink,
  FiMail,
  FiPrinter,
  FiRepeat,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useConvertDocMutation,
  useDuplicateDocMutation,
  useGetSingleDocQuery,
} from "../documentsApiSlice";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import { useGetBusinessQuery } from "../../business/businessApiSlice";
import { RefusalNote, useBusiness } from "../../business/permissions";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import { useDocumentPdf } from "../useDocumentPdf";
import EmailDocumentDialog from "../components/EmailDocumentDialog";
import ActivityTimeline from "../components/ActivityTimeline";
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
  StatusBadge,
  Amount,
  LoadingState,
  ErrorState,
  Badge,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
  secondaryIconButton,
  textLink,
} from "../../../components/workspace/pastel";
import PaymentForm from "./PaymentForm";
import PaymentsLedger from "../components/PaymentsLedger";

const DEMO_EMAIL_NOTE = "Emailing is turned off for the shared demo account.";
const partyHeading = "mb-3 text-[12.5px] font-normal text-spot-muted";
const partyName = "mb-1.5 block text-[15px] font-medium text-spot-ink";
const partyLine =
  "text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]";
const copy =
  "whitespace-pre-wrap text-[14px] leading-[1.7] [overflow-wrap:anywhere]";

const SingleDocumentPage = () => {
  const { id } = useParams();
  const query = useGetSingleDocQuery(id);
  const profileQuery = useGetUserProfileQuery();
  const settings = useGetSettingsQuery();
  const document = query.data?.document;
  const profile = profileQuery.data?.userProfile;
  const business = useGetBusinessQuery().data?.business;
  const { reason } = useBusiness();
  const editRefusal = reason("EDIT_RECORDS");
  // Sending is refused to viewers, and turned off for the demo account.
  const sendNote = editRefusal || (profile?.demo ? DEMO_EMAIL_NOTE : undefined);
  const sendNoteId = editRefusal ? "document-edit-refusal" : "demo-email-note";
  // Shown only when it differs from the name the business trades under.
  const legalName =
    business?.legalName &&
    business.legalName.trim().toLowerCase() !==
      business.name?.trim().toLowerCase()
      ? business.legalName
      : null;
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
  useTitle(`${document?.documentNumber || "Document"} — Invoice Spot`);
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
      toast.success(
        `Copied as ${newDocument.documentNumber}. Review it before sending.`,
      );
      navigate(`/edit-doc/${newDocument._id}`);
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be copied."));
    }
  };
  const convert = async () => {
    try {
      const { newDocument } = await convertDoc(id).unwrap();
      toast.success(
        `Invoice ${newDocument.documentNumber} created from ${document.documentNumber}.`,
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
      render: (item) => item.itemName,
    },
    { label: "Quantity", render: (item) => item.quantity },
    {
      label: "Unit price",
      type: "amount",
      render: (item) => <Amount value={item.unitPrice} />,
    },
    { label: "Discount (%)", render: (item) => item.discount || "0" },
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
        title={document?.documentNumber || "Document"}
        description={
          document
            ? `${document.documentType} for ${document.customer?.name || "your customer"}`
            : "Your document details."
        }
        actions={
          document && (
            <>
              <ActionLink to={`/edit-doc/${id}`} secondary reason={editRefusal}>
                <FiEdit2 aria-hidden="true" /> Edit document
              </ActionLink>
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
              {document.documentType === "Quotation" && !invoicedAs.length && (
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
              {document.documentType === "Invoice" &&
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
              {profile?.demo && (
                <span id="demo-email-note" className="sr-only">
                  {DEMO_EMAIL_NOTE}
                </span>
              )}
              <RefusalNote id="document-edit-refusal" reason={editRefusal} />
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
            <Panel
              title={document.documentType}
              aside={
                <span className="flex flex-wrap justify-end gap-1.5">
                  {document.acceptedAt && (
                    <span
                      title={`Accepted on ${dateLabel(document.acceptedAt)}`}
                    >
                      <Badge tone="paid">Accepted</Badge>
                    </span>
                  )}
                  <StatusBadge status={document.status} />
                </span>
              }
            >
              <div className="grid grid-cols-3 gap-8 max-[960px]:grid-cols-2 max-[700px]:grid-cols-1 max-[700px]:gap-6">
                <div className="min-w-0">
                  <h3 className={partyHeading}>From</h3>
                  {business?.logo && (
                    <img
                      className="mb-3 max-h-14 max-w-[120px] object-contain"
                      src={business.logo}
                      alt={`${business.name} logo`}
                    />
                  )}
                  <strong className={partyName}>{business?.name}</strong>
                  {[
                    legalName,
                    business?.email,
                    business?.phoneNumber,
                    business?.address,
                    business?.city,
                    [business?.state, business?.postalCode]
                      .filter(Boolean)
                      .join(" "),
                    business?.country,
                    business?.taxId && `Tax ID ${business.taxId}`,
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
                      ["Issue date", document.createdAt],
                      ["Due date", document.dueDate],
                    ].map(([label, value]) => (
                      <div key={label}>
                        <dt className={partyLine}>{label}</dt>
                        <dd className="text-[15px] text-spot-ink">
                          {dateLabel(value)}
                        </dd>
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
                                {link.documentNumber}
                              </Link>
                            ))}
                          </dd>
                        </div>
                      ))}
                    {document.documentType === "Invoice" &&
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
                    [
                      `${settings.data?.settings?.taxLabel || "Tax"} (${document.rates || 0}%)`,
                      document.salesTax,
                    ],
                    ["Cumulative total", document.total],
                    ["Amount paid", totalAmountReceived],
                    [
                      "Balance",
                      Number(document.total || 0) - totalAmountReceived,
                    ],
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
            {document.documentType === "Invoice" &&
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
