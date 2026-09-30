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

// A draft has no payment status until it is issued, and a void document
// none any more. A quotation takes no payments: it shows how the client
// answered, or whether it is still open.
export const DocumentStatusBadge = ({ document }) =>
  isDraft(document) ? (
    <Badge>Draft</Badge>
  ) : isVoid(document) ? (
    <Badge tone="unpaid">Void</Badge>
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
