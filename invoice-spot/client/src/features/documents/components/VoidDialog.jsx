import { useState } from "react";
import { FiSlash } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { Field, errorMessage } from "../../../components/workspace/Workspace";
import {
  dangerButton,
  errorBanner,
  errorTitle,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useVoidDocMutation } from "../documentsApiSlice";

const VoidForm = ({ document, onClose }) => {
  const [reason, setReason] = useState("");
  // Why the server refused, word for word: payments to delete first, or a
  // schedule to stop.
  const [refusal, setRefusal] = useState(null);
  const [voidDoc, { isLoading: voiding }] = useVoidDocMutation();
  const name = `${document.documentType.toLowerCase()} ${document.documentNumber}`;
  const submit = async (event) => {
    event.preventDefault();
    setRefusal(null);
    try {
      const result = await voidDoc({ id: document._id, reason }).unwrap();
      toast.success(result.message || "The document is void.");
      onClose();
    } catch (error) {
      if (error?.status === 409) {
        setRefusal(errorMessage(error));
      } else {
        toast.error(errorMessage(error, "The document could not be voided."));
      }
    }
  };
  return (
    <form className="mt-5 flex flex-col gap-5" onSubmit={submit}>
      <ul className="m-0 flex list-disc flex-col gap-1.5 pl-5 text-[14px] leading-[1.6] text-spot-body">
        <li>It keeps its number, {document.documentNumber}.</li>
        <li>It no longer counts toward balances, reports or reminders.</li>
        <li>Its PDF and its share link show it as void, with your reason.</li>
        <li>This can’t be undone. Duplicate it to start again.</li>
      </ul>
      {refusal && (
        <p className={errorBanner} role="alert">
          <strong className={errorTitle}>{refusal}</strong>
        </p>
      )}
      <Field
        label="Reason"
        as="textarea"
        rows={3}
        required
        minLength={3}
        maxLength={500}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        disabled={voiding}
        hint="Your client sees it on the PDF and the share page."
        data-autofocus
      />
      <div className="mt-2 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={voiding}
        >
          Cancel
        </button>
        <button type="submit" className={dangerButton} disabled={voiding}>
          <FiSlash aria-hidden="true" />
          {voiding ? "Voiding…" : `Void ${name}`}
        </button>
      </div>
    </form>
  );
};

// Voids an issued document, with the reason its client will see.
const VoidDialog = ({ document, open, onClose }) => (
  <Dialog
    open={open}
    title={`Void ${document.documentNumber}?`}
    onClose={onClose}
    dismissible={false}
    size="medium"
  >
    {open && <VoidForm document={document} onClose={onClose} />}
  </Dialog>
);

export default VoidDialog;
