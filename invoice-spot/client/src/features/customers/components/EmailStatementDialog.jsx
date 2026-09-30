import { toast } from "react-toastify";
import { errorMessage } from "../../../components/workspace/Workspace";
import { EmailDialog } from "../../documents/components/EmailDocumentDialog";
import {
  useEmailStatementMutation,
  useGetStatementEmailDraftQuery,
} from "../customersApiSlice";

// Emails a client's statement of account as a PDF.
const EmailStatementDialog = ({ customerId, open, onClose }) => {
  const draft = useGetStatementEmailDraftQuery(customerId, { skip: !open });
  const [send, { isLoading: sending }] = useEmailStatementMutation();
  const sendEmail = async (form) => {
    try {
      const result = await send({ id: customerId, ...form }).unwrap();
      toast.success(result.message || "Statement sent successfully.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The statement could not be emailed."));
    }
  };
  return (
    <EmailDialog
      title="Email statement"
      open={open}
      onClose={onClose}
      draft={draft}
      sending={sending}
      onSend={sendEmail}
    />
  );
};

export default EmailStatementDialog;
