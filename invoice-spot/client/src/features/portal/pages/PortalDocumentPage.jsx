import { Link, useParams, useSearchParams } from "react-router-dom";
import { FiArrowLeft } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useAcceptPortalDocumentMutation,
  useDeclinePortalDocumentMutation,
  useGetPortalDocumentQuery,
  useGetPortalPdfMutation,
  useReportPortalPaymentMutation,
} from "../portalApiSlice";
import { saveFile } from "../../documents/useDocumentPdf";
import { errorMessage } from "../../../components/workspace/Workspace";
import { panelNote, textLink } from "../../../components/workspace/pastel";
import PublicDocument from "../../public/components/PublicDocument";
import { Notice, frame } from "../../public/components/PublicPage";

// One document from a client's portal, as its share link would show it.
const PortalDocumentPage = () => {
  const { token } = useParams();
  const [params] = useSearchParams();
  const number = params.get("number") || "";
  const query = useGetPortalDocumentQuery({ token, number });
  const [accept, { isLoading: accepting }] = useAcceptPortalDocumentMutation();
  const [decline, { isLoading: declining }] =
    useDeclinePortalDocumentMutation();
  const [report] = useReportPortalPaymentMutation();
  const [getPdf, { isLoading: downloading }] = useGetPortalPdfMutation();
  const doc = query.data;
  useTitle(
    doc
      ? `${doc.documentType} ${doc.documentNumber} from ${doc.business.name}`
      : "Your document — Invoice Spot",
  );
  const download = async () => {
    try {
      saveFile(
        await getPdf({ token, number }).unwrap(),
        `${number.replace(/[^A-Za-z0-9._-]/g, "-")}.pdf`,
      );
    } catch (error) {
      toast.error(errorMessage(error, "The PDF could not be prepared."));
    }
  };
  return (
    <div className={frame}>
      <div className="w-full max-w-[1000px]">
        <Link
          className={`${textLink} inline-flex items-center gap-1.5 text-[13.5px]`}
          to={`/c/${token}`}
        >
          <FiArrowLeft aria-hidden="true" /> All your documents
        </Link>
      </div>
      {query.isLoading ? (
        <p className={panelNote} role="status">
          Opening the document…
        </p>
      ) : query.isError ? (
        query.error?.status === 404 ? (
          <Notice
            title="We couldn’t find this document"
            text={errorMessage(
              query.error,
              "It isn’t among your documents. Go back to see them all.",
            )}
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
          accept={() => accept({ token, number }).unwrap()}
          accepting={accepting}
          decline={(reason) => decline({ token, number, reason }).unwrap()}
          declining={declining}
          report={(payment) => report({ token, number, ...payment }).unwrap()}
        />
      )}
      <p className="text-[13px] text-spot-muted">
        Shared with <span className="text-spot-text">Invoice Spot</span>
      </p>
    </div>
  );
};

export default PortalDocumentPage;
