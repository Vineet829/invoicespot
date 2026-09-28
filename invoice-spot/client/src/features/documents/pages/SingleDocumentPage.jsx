import { useParams } from "react-router-dom";
import { FiEdit2, FiMail } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { useGetSingleDocQuery } from "../documentsApiSlice";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import { useSendDocumentEmailMutation } from "../../workspace/workspaceApiSlice";
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
  errorMessage,
} from "../../../components/workspace/Workspace";
import { primaryButton } from "../../../components/workspace/pastel";
import { lineAmount } from "./documentEditorModel";
import PaymentForm from "./PaymentForm";

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
  const document = query.data?.document;
  const profile = profileQuery.data?.userProfile;
  const [send, { isLoading: sending }] = useSendDocumentEmailMutation();
  useTitle(`${document?.documentNumber || "Document"} — Invoice Spot`);
  const totalAmountReceived = (document?.paymentRecords || []).reduce(
    (sum, payment) => sum + Number(payment.amountPaid || 0),
    0,
  );
  const sendEmail = async () => {
    try {
      const result = await send({
        profile,
        document,
        status: document.status,
        totalAmountReceived,
      }).unwrap();
      toast.success(result.message || "Document sent successfully.");
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be emailed."));
    }
  };
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
        <Amount value={lineAmount(item)} currency={document?.currency} />
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
                className={primaryButton}
                onClick={sendEmail}
                disabled={sending || !profile}
              >
                <FiMail aria-hidden="true" />
                {sending ? "Sending…" : "Email document"}
              </button>
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
                <StatusBadge
                  status={
                    totalAmountReceived >= document.total
                      ? "Paid"
                      : document.status
                  }
                />
              }
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
                    {document.customer?.name}
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
                      `VAT / sales tax (${document.rates || 0}%)`,
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
            <Panel
              title="Record payment"
              description={`Record a payment from ${document.customer?.name || "your customer"}.`}
            >
              <PaymentForm document={document} />
            </Panel>
          </>
        )
      )}
    </WorkspacePage>
  );
};
export default SingleDocumentPage;
