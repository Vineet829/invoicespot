import { Link } from "react-router-dom";
import {
  Amount,
  DataTable,
  Panel,
  dateLabel,
} from "../../../components/workspace/Workspace";
import { textLink } from "../../../components/workspace/pastel";
import { DocumentStatusBadge, documentName } from "../documentState";

// The credit notes made against an invoice or receipt, drafts and void ones
// included. Only issued ones take their total off.
const CreditNotesPanel = ({ document }) => (
  <Panel
    title="Credit notes"
    description="Issued credit notes take their total off what this document bills."
  >
    <DataTable
      label="Credit notes"
      narrow
      rows={document.creditNotes}
      columns={[
        {
          label: "Credit note",
          type: "main",
          render: (note) => (
            <Link className={textLink} to={`/document/${note._id}`}>
              {documentName({ ...note, documentType: "Credit Note" })}
            </Link>
          ),
        },
        {
          label: "Issued",
          render: (note) => (note.issueDate ? dateLabel(note.issueDate) : "—"),
        },
        {
          label: "State",
          render: (note) => (
            <DocumentStatusBadge
              document={{ ...note, documentType: "Credit Note" }}
            />
          ),
        },
        {
          label: "Amount",
          type: "amount",
          render: (note) => (
            <Amount value={note.total} currency={document.currency} />
          ),
        },
      ]}
    />
  </Panel>
);

export default CreditNotesPanel;
