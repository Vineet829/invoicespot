import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FiDownload, FiEdit2, FiMail, FiPrinter } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetSingleDocQuery } from "../documentsApiSlice";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import { useDocumentPdf } from "../useDocumentPdf";
import EmailDocumentDialog from "../components/EmailDocumentDialog";
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
  dateLabel,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
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
  const [emailing, setEmailing] = useState(false);
  const pdf = useDocumentPdf(document);
  useTitle(`${document?.documentNumber || "Document"} — Invoice Spot`);
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
              <ActionLink to={`/edit-doc/${id}`} secondary>
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
                className={primaryButton}
                onClick={() => setEmailing(true)}
                disabled={profile?.demo}
                title={profile?.demo ? DEMO_EMAIL_NOTE : undefined}
                aria-describedby={profile?.demo ? "demo-email-note" : undefined}
              >
                <FiMail aria-hidden="true" /> Email document
              </button>
              {profile?.demo && (
                <span id="demo-email-note" className="sr-only">
                  {DEMO_EMAIL_NOTE}
                </span>
              )}
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
              aside={<StatusBadge status={document.status} />}
            >
              <div className="grid grid-cols-3 gap-8 max-[960px]:grid-cols-2 max-[700px]:grid-cols-1 max-[700px]:gap-6">
                <div className="min-w-0">
                  <h3 className={partyHeading}>From</h3>
                  {profile?.avatar && (
                    <img
                      className="mb-3 max-h-14 max-w-[120px] object-contain"
                      src={profile.avatar}
                      alt={`${profile.businessName || "Business"} logo`}
                    />
                  )}
                  <strong className={partyName}>
                    {profile?.businessName ||
                      `${profile?.firstName || ""} ${profile?.lastName || ""}`}
                  </strong>
                  {[
                    profile?.email,
                    profile?.phoneNumber,
                    profile?.address,
                    profile?.city,
                    profile?.country,
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
                <PaymentsLedger document={document} />
              </Panel>
            )}
            {document.documentType === "Invoice" &&
              document.status !== "Paid" && (
                <Panel
                  title="Record payment"
                  description={`Record a payment from ${document.customer?.name || "your customer"}.`}
                >
                  <PaymentForm document={document} />
                </Panel>
              )}
          </>
        )
      )}
      <EmailDocumentDialog
        documentId={id}
        open={emailing}
        onClose={() => setEmailing(false)}
      />
    </WorkspacePage>
  );
};
export default SingleDocumentPage;
