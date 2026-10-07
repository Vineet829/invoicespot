import { useParams } from "react-router-dom";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useAcceptSharedDocumentMutation,
  useDeclineSharedDocumentMutation,
  useGetSharedDocumentQuery,
  useGetSharedPdfMutation,
  useReportSharedPaymentMutation,
} from "../publicApiSlice";
import { saveFile } from "../../documents/useDocumentPdf";
import { errorMessage } from "../../../components/workspace/Workspace";
import { panelNote } from "../../../components/workspace/pastel";
import PublicDocument from "../components/PublicDocument";
import { Notice, frame } from "../components/PublicPage";

// A document someone shared by link. The link itself is the key: no sign-in.
const SharedDocumentPage = () => {
  const { token } = useParams();
  const query = useGetSharedDocumentQuery(token);
  const [accept, { isLoading: accepting }] = useAcceptSharedDocumentMutation();
  const [decline, { isLoading: declining }] =
    useDeclineSharedDocumentMutation();
  const [report] = useReportSharedPaymentMutation();
  const [getPdf, { isLoading: downloading }] = useGetSharedPdfMutation();
  const doc = query.data;
  useTitle(
    doc
      ? `${doc.documentType} ${doc.documentNumber} from ${doc.business.name}`
      : "Shared document — Invoice Spot",
  );
  const download = async () => {
    try {
      const url = await getPdf(token).unwrap();
      saveFile(
        url,
        `${doc.documentNumber.replace(/[^A-Za-z0-9._-]/g, "-")}.pdf`,
      );
    } catch (error) {
      toast.error(errorMessage(error, "The PDF could not be prepared."));
    }
  };
  return (
    <div className={frame}>
      {query.isLoading ? (
        <p className={panelNote} role="status">
          Opening the document…
        </p>
      ) : query.isError ? (
        query.error?.status === 404 ? (
          <Notice
            title="This link doesn’t work anymore"
            text="It may have been turned off or replaced. Ask the sender for a new link."
          />
        ) : (
          <Notice
            title="We couldn’t open this document"
            text={errorMessage(query.error)}
            retry={query.refetch}
          />
        )
      ) : (
        <PublicDocument
          doc={doc}
          onDownload={download}
          downloading={downloading}
          accept={() => accept(token).unwrap()}
          accepting={accepting}
          decline={(reason) => decline({ token, reason }).unwrap()}
          declining={declining}
          report={(payment) => report({ token, ...payment }).unwrap()}
        />
      )}
      <p className="text-[13px] text-spot-muted">
        Shared with <span className="text-spot-text">Invoice Spot</span>
      </p>
    </div>
  );
};

export default SharedDocumentPage;
