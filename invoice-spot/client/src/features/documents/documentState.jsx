import { Badge, StatusBadge } from "../../components/workspace/Workspace";

export const isDraft = (document) => document?.state === "draft";

export const isVoid = (document) => document?.state === "void";

// What to call a document: its number once it is issued, or what it is
// while it is still a draft.
export const documentName = (document) =>
  document?.documentNumber ||
  (isDraft(document)
    ? `Draft ${document.documentType.toLowerCase()}`
    : "Document");

// Settled by credit notes alone, with no money received.
export const isCredited = (document) =>
  document?.status === "Paid" &&
  Number(document.totalAmountReceived) === 0 &&
  Number(document.ledger?.totalCredited) > 0;

// A draft has no payment status until it is issued, and a void document
// none any more. A quotation takes no payments: it shows how the client
// answered, or whether it is still open. A credit note is only issued.
export const DocumentStatusBadge = ({ document }) =>
  isDraft(document) ? (
    <Badge>Draft</Badge>
  ) : isVoid(document) ? (
    <Badge tone="unpaid">Void</Badge>
  ) : document.documentType === "Credit Note" ? (
    <Badge tone="paid">Issued</Badge>
  ) : isCredited(document) ? (
    <Badge tone="paid">Credited</Badge>
  ) : document.documentType === "Quotation" ? (
    document.acceptedAt ? (
      <Badge tone="paid">Accepted</Badge>
    ) : document.declinedAt ? (
      <Badge tone="unpaid">Declined</Badge>
    ) : document.expired ? (
      <Badge tone="partial">Expired</Badge>
    ) : (
      <Badge>Open</Badge>
    )
  ) : (
    <StatusBadge status={document.status} />
  );
