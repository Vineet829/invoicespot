import { useState } from "react";
import { FiSend } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { Field, errorMessage } from "../../../components/workspace/Workspace";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  primaryButton,
  retryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useEmailDocumentMutation,
  useGetEmailDraftQuery,
} from "../documentsApiSlice";

const EmailForm = ({ draft, sending, onSend, onClose }) => {
  const [form, setForm] = useState({
    to: draft.to || "",
    subject: draft.subject || "",
    message: draft.message || "",
  });
  const update = (key) => (event) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSend(form);
      }}
    >
      <Field
        label="To"
        type="email"
        required
        maxLength={254}
        autoComplete="email"
        value={form.to}
        onChange={update("to")}
        autoFocus
        data-autofocus
      />
      <Field
        label="Subject"
        required
        maxLength={200}
        value={form.subject}
        onChange={update("subject")}
      />
      <Field
        label="Message"
        as="textarea"
        required
        rows={7}
        maxLength={5000}
        value={form.message}
        onChange={update("message")}
        hint="The PDF is attached, and replies come to your email address."
      />
      <div className="mt-3 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={sending}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={sending}>
          <FiSend aria-hidden="true" />
          {sending ? "Sending…" : "Send email"}
        </button>
      </div>
    </form>
  );
};

// Emails a PDF. The server suggests the recipient, subject and message in
// `draft`; each can be changed before sending.
export const EmailDialog = ({
  title,
  open,
  onClose,
  draft,
  sending,
  onSend,
}) => (
  <Dialog
    open={open}
    title={title}
    onClose={onClose}
    busy={sending}
    dismissible={false}
    size="medium"
  >
    {draft.isFetching || draft.isUninitialized ? (
      <p className="mt-4 text-[13.5px] text-spot-muted" role="status">
        Preparing the email…
      </p>
    ) : draft.isError ? (
      <div className={`${errorBanner} mt-5`} role="alert">
        <div className="flex-1">
          <strong className={errorTitle}>We couldn’t prepare the email.</strong>
          <span className={errorDetail}>{errorMessage(draft.error)}</span>
        </div>
        <button type="button" className={retryButton} onClick={draft.refetch}>
          Try again
        </button>
      </div>
    ) : (
      <EmailForm
        key={draft.fulfilledTimeStamp}
        draft={draft.data}
        sending={sending}
        onSend={onSend}
        onClose={onClose}
      />
    )}
  </Dialog>
);

const EmailDocumentDialog = ({ documentId, open, onClose }) => {
  const draft = useGetEmailDraftQuery(documentId, { skip: !open });
  const [send, { isLoading: sending }] = useEmailDocumentMutation();
  const sendEmail = async (form) => {
    try {
      const result = await send({ id: documentId, ...form }).unwrap();
      toast.success(result.message || "Document sent successfully.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be emailed."));
    }
  };
  return (
    <EmailDialog
      title="Email document"
      open={open}
      onClose={onClose}
      draft={draft}
      sending={sending}
      onSend={sendEmail}
    />
  );
};

export default EmailDocumentDialog;
