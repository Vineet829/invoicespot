import { useState } from "react";
import { FiAlertCircle, FiCheck, FiUpload } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { errorMessage } from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  fileControl,
  panelNote,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useImportClientsMutation,
  usePreviewClientImportMutation,
} from "../customersApiSlice";

const plural = (count, noun) => `${count} ${noun}${count === 1 ? "" : "s"}`;

// Choosing a file shows every row first, each marked ready or with what
// stops it; only then are the ready rows added.
const ImportForm = ({ onClose }) => {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [problem, setProblem] = useState("");
  const [check, { isLoading: checking }] = usePreviewClientImportMutation();
  const [add, { isLoading: adding }] = useImportClientsMutation();
  const busy = checking || adding;
  const choose = async (event) => {
    const chosen = event.target.files?.[0] || null;
    setFile(chosen);
    setPreview(null);
    setProblem("");
    if (!chosen) return;
    try {
      setPreview(await check(chosen).unwrap());
    } catch (error) {
      setProblem(errorMessage(error, "This file could not be checked."));
    }
  };
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await add(file).unwrap();
      toast.success(result.message);
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The clients could not be added."));
    }
  };
  const skipped = preview ? preview.total - preview.ready : 0;
  return (
    <form className="mt-2 flex flex-col gap-5" onSubmit={submit}>
      <p className={panelNote}>
        The first row names the columns. Name, Email and Phone are needed;
        VAT/TIN, Address, City and Country are optional. A clients file exported
        from Invoice Spot works as it is.
      </p>
      <label className="flex flex-col gap-2">
        <span className={fieldLabel}>CSV file</span>
        <input
          type="file"
          accept=".csv,text/csv"
          className={fileControl}
          onChange={choose}
          disabled={busy}
          data-autofocus
        />
      </label>
      <div role="status" aria-live="polite" className="empty:hidden">
        {checking
          ? "Checking the file…"
          : preview &&
            `${plural(preview.total, "row")}: ${preview.ready} ready${skipped ? `, ${skipped} with problems` : ""}.`}
      </div>
      {problem && (
        <p
          className="flex items-start gap-2 text-[13px] text-overdue-strong [&>svg]:mt-0.5 [&>svg]:size-4 [&>svg]:shrink-0"
          role="alert"
        >
          <FiAlertCircle aria-hidden="true" />
          {problem}
        </p>
      )}
      {preview && preview.rows.length > 0 && (
        <ul
          className="m-0 max-h-[300px] list-none overflow-y-auto rounded-2xl border border-[#23273514] p-0"
          aria-label="Rows in the file"
        >
          {preview.rows.map((row) => (
            <li
              key={row.row}
              className="flex gap-3 px-4 py-3 text-[13px] [&:not(:first-child)]:border-t [&:not(:first-child)]:border-[#23273512]"
            >
              <span className="w-12 shrink-0 tabular-nums text-spot-faint">
                Row {row.row}
              </span>
              <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">
                <span className="block font-medium text-spot-ink">
                  {row.name || "No name"}
                </span>
                <span className="block text-spot-muted">
                  {[row.email, row.phoneNumber, row.city]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
                {row.problems.map((text) => (
                  <span key={text} className="mt-1 block text-overdue-strong">
                    {text}
                  </span>
                ))}
              </span>
              {row.problems.length === 0 && (
                <span className="flex shrink-0 items-start gap-1 text-spot-violet [&>svg]:mt-0.5 [&>svg]:size-3.5">
                  <FiCheck aria-hidden="true" /> Ready
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className="flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || !preview?.ready}
        >
          <FiUpload aria-hidden="true" />
          {adding
            ? "Adding…"
            : preview?.ready
              ? `Add ${plural(preview.ready, "client")}`
              : "Add clients"}
        </button>
      </div>
    </form>
  );
};

const ImportClientsDialog = ({ open, onClose }) => (
  <Dialog
    open={open}
    title="Import clients"
    onClose={onClose}
    dismissible={false}
    size="medium"
  >
    {open && <ImportForm onClose={onClose} />}
  </Dialog>
);

export default ImportClientsDialog;
