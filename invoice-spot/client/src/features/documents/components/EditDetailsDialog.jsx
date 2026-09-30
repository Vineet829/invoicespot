import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Field,
  dateInput,
  dateInstant,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useUpdateDocMutation } from "../documentsApiSlice";

const DetailsForm = ({ document, onClose }) => {
  const receipt = document.documentType === "Receipt";
  const quotation = document.documentType === "Quotation";
  const [dueDate, setDueDate] = useState(
    document.dueDate ? dateInput(document.dueDate) : "",
  );
  const [notes, setNotes] = useState(document.additionalInfo || "");
  const [terms, setTerms] = useState(document.termsConditions || "");
  const [update, { isLoading: saving }] = useUpdateDocMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await update({
        id: document._id,
        ...(!receipt && dueDate && { dueDate: dateInstant(dueDate) }),
        additionalInfo: notes,
        termsConditions: terms,
      }).unwrap();
      toast.success(result.message || "The document was updated.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be updated."));
    }
  };
  return (
    <form className="mt-5 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        {document.documentNumber} keeps its client, lines and amounts as it went
        out. To change those, void it and duplicate it.
      </p>
      {!receipt && (
        <Field
          label={quotation ? "Valid until" : "Due date"}
          type="date"
          required={Boolean(document.dueDate)}
          value={dueDate}
          onChange={(event) => setDueDate(event.target.value)}
          disabled={saving}
          data-autofocus
        />
      )}
      <Field
        label="Additional info"
        as="textarea"
        rows={4}
        maxLength={5000}
        value={notes}
        onChange={(event) => setNotes(event.target.value)}
        disabled={saving}
        {...(receipt && { "data-autofocus": true })}
      />
      <Field
        label="Terms & conditions"
        as="textarea"
        rows={4}
        maxLength={5000}
        value={terms}
        onChange={(event) => setTerms(event.target.value)}
        disabled={saving}
      />
      <div className="mt-2 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={saving}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={saving}>
          <FiCheck aria-hidden="true" />
          {saving ? "Saving…" : "Save details"}
        </button>
      </div>
    </form>
  );
};

// What still changes on an issued document: when it is due, and its notes
// and terms.
const EditDetailsDialog = ({ document, open, onClose }) => (
  <Dialog
    open={open}
    title="Edit details"
    onClose={onClose}
    dismissible={false}
    size="medium"
  >
    {open && <DetailsForm document={document} onClose={onClose} />}
  </Dialog>
);

export default EditDetailsDialog;
