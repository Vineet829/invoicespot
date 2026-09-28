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
  Badge,
  LoadingState,
  ErrorState,
  statusTone,
  dateLabel,
  money,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { lineAmount } from "./documentEditorModel";
import PaymentForm from "./PaymentForm";
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
      className: "workspace-cell-index",
      render: (_item, index) => index + 1,
    },
    {
      label: "Product / service",
      className: "workspace-cell-main",
      render: (item) => item.itemName,
    },
    { label: "Quantity", render: (item) => item.quantity },
    {
      label: "Unit price",
      className: "workspace-cell-amount",
      render: (item) => money(item.unitPrice),
    },
    { label: "Discount (%)", render: (item) => item.discount || "0" },
    {
      label: "Line total",
      className: "workspace-cell-amount",
      render: (item) => money(lineAmount(item), document?.currency),
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
                className="workspace-button"
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
                <Badge
                  tone={statusTone(
                    totalAmountReceived >= document.total
                      ? "Paid"
                      : document.status,
                  )}
                >
                  {totalAmountReceived >= document.total
                    ? "Paid"
                    : document.status}
                </Badge>
              }
            >
              <div className="workspace-document-summary">
                <div>
                  <h3>From</h3>
                  {profile?.avatar && (
                    <img
                      className="workspace-logo-preview"
                      src={profile.avatar}
                      alt={`${profile.businessName || "Business"} logo`}
                    />
                  )}
                  <strong>
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
                      <p key={index}>{value}</p>
                    ))}
                </div>
                <div>
                  <h3>For</h3>
                  <strong>{document.customer?.name}</strong>
                  <p>Customer no. {document.customer?.accountNo || "—"}</p>
                  <p>VAT / TIN: {document.customer?.vatTinNo || "—"}</p>
                  {[
                    document.customer?.email,
                    document.customer?.phoneNumber,
                    document.customer?.address,
                    document.customer?.city,
                    document.customer?.country,
                  ]
                    .filter(Boolean)
                    .map((value, index) => (
                      <p key={index}>{value}</p>
                    ))}
                </div>
                <div>
                  <h3>Document details</h3>
                  <p>Issue date</p>
                  <strong>{dateLabel(document.createdAt)}</strong>
                  <p>Due date</p>
                  <strong>{dateLabel(document.dueDate)}</strong>
                  <p>Total amount</p>
                  <strong>{money(document.total, document.currency)}</strong>
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
            <div className="workspace-detail-grid">
              <div>
                <Panel title="Additional info">
                  <p className="workspace-copy">
                    {document.additionalInfo || "No additional info."}
                  </p>
                </Panel>
                <Panel title="Terms & conditions">
                  <p className="workspace-copy">
                    {document.termsConditions || "No terms provided."}
                  </p>
                </Panel>
              </div>
              <Panel title="Cost summary">
                <dl className="workspace-summary-list">
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
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{money(value, document.currency)}</dd>
                    </div>
                  ))}
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
