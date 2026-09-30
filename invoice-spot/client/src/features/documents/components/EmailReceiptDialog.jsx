import { toast } from "react-toastify";
import { errorMessage } from "../../../components/workspace/Workspace";
import { EmailDialog } from "./EmailDocumentDialog";
import {
  useEmailReceiptMutation,
  useGetReceiptEmailDraftQuery,
} from "../documentsApiSlice";

// Emails the receipt for one payment as a PDF.
const EmailReceiptDialog = ({ documentId, paymentId, onClose }) => {
  const open = Boolean(paymentId);
  const draft = useGetReceiptEmailDraftQuery(
    { id: documentId, paymentId },
    { skip: !open },
  );
  const [send, { isLoading: sending }] = useEmailReceiptMutation();
  const sendEmail = async (form) => {
    try {
      const result = await send({
        id: documentId,
        paymentId,
        ...form,
      }).unwrap();
      toast.success(result.message || "Receipt sent successfully.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The receipt could not be emailed."));
    }
  };
  return (
    <EmailDialog
      title="Email receipt"
      open={open}
      onClose={onClose}
      draft={draft}
      sending={sending}
      onSend={sendEmail}
    />
  );
};

export default EmailReceiptDialog;
