import { toast } from "react-toastify";
import { errorMessage } from "../components/workspace/Workspace";
import { saveFile } from "../features/documents/useDocumentPdf";

// Saves the file a download mutation fetches under `fileName`, or says why it
// could not be prepared. The flag is true while the file is on its way.
const useFileDownload = (useFileMutation) => {
  const [fetchFile, { isLoading }] = useFileMutation();
  const download = async (arg, fileName) => {
    try {
      saveFile(await fetchFile(arg).unwrap(), fileName);
    } catch (error) {
      toast.error(errorMessage(error, "The file could not be prepared."));
    }
  };
  return [download, isLoading];
};

export default useFileDownload;
