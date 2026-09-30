import { useState } from "react";
import { toast } from "react-toastify";
import { errorMessage } from "../../components/workspace/Workspace";
import { useGetDocumentPdfMutation } from "./documentsApiSlice";

export const pdfFileName = (document) =>
  `${(document?.documentNumber || "document").replace(/[^A-Za-z0-9._-]/g, "-")}.pdf`;

export const saveFile = (url, name) => {
  const link = window.document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
};

// Prints from a hidden frame, so no new tab opens. The frame lives outside
// #root, where no Tailwind classes apply, so it is hidden with inline styles.
let printFrame;
const printFile = (url) => {
  if (printFrame) {
    URL.revokeObjectURL(printFrame.src);
    printFrame.remove();
  }
  printFrame = window.document.createElement("iframe");
  printFrame.title = "Document to print";
  printFrame.style.cssText =
    "position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden";
  printFrame.onload = () => {
    try {
      printFrame.contentWindow.focus();
      printFrame.contentWindow.print();
    } catch {
      window.open(url, "_blank", "noopener");
    }
  };
  printFrame.src = url;
  window.document.body.appendChild(printFrame);
};

// Download and Print for one document. `busy` names the action in progress.
export const useDocumentPdf = (document) => {
  const [getPdf] = useGetDocumentPdfMutation();
  const [busy, setBusy] = useState(null);
  const run = async (action) => {
    setBusy(action);
    try {
      const url = await getPdf(document._id).unwrap();
      if (action === "download") saveFile(url, pdfFileName(document));
      else printFile(url);
    } catch (error) {
      toast.error(errorMessage(error, "The PDF could not be prepared."));
    } finally {
      setBusy(null);
    }
  };
  return {
    busy,
    download: () => run("download"),
    print: () => run("print"),
  };
};
