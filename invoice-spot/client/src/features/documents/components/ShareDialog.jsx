import { useState } from "react";
import { FiCopy, FiLink, FiRefreshCw } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { errorMessage } from "../../../components/workspace/Workspace";
import {
  inputControl,
  panelNote,
  primaryButton,
  retryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useCreateShareLinkMutation,
  useGetShareStatusQuery,
  useRevokeShareLinkMutation,
} from "../documentsApiSlice";

const moment = (value) =>
  new Date(value).toLocaleString("en", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

const actions = "mt-6 flex flex-wrap justify-end gap-2.5";

// Shares a read-only copy of a document by link. The server keeps only a
// hash of the link, so it is shown once, right after it is made; a new link
// replaces the old one.
const ShareDialog = ({ document, open, onClose }) => {
  const status = useGetShareStatusQuery(document._id, { skip: !open });
  const [create, { isLoading: creating }] = useCreateShareLinkMutation();
  const [revoke, { isLoading: revoking }] = useRevokeShareLinkMutation();
  const [url, setUrl] = useState(null);
  const busy = creating || revoking;
  const close = () => {
    setUrl(null);
    onClose();
  };
  const makeLink = async () => {
    try {
      setUrl((await create(document._id).unwrap()).url);
    } catch (error) {
      toast.error(errorMessage(error, "The link could not be created."));
    }
  };
  const turnOff = async () => {
    try {
      const result = await revoke(document._id).unwrap();
      setUrl(null);
      toast.success(result.message || "The share link was turned off.");
    } catch (error) {
      toast.error(errorMessage(error, "The link could not be turned off."));
    }
  };
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      toast.success("Link copied.");
    } catch {
      toast.error("The link couldn’t be copied. Select it in the box instead.");
    }
  };
  const quotation = document.documentType === "Quotation";
  const link = status.data;
  return (
    <Dialog
      open={open}
      title="Share link"
      onClose={close}
      busy={busy}
      size="medium"
    >
      <p className="mt-3 text-[14px] leading-[1.6] text-spot-body">
        Anyone with the link can view this {document.documentType.toLowerCase()}{" "}
        and download its PDF{quotation ? ", and accept it" : ""}. No sign-in is
        needed.
      </p>
      {url ? (
        <>
          <label className="mt-5 block">
            <span className="sr-only">Share link</span>
            <input
              className={inputControl}
              readOnly
              value={url}
              autoFocus
              onFocus={(event) => event.target.select()}
            />
          </label>
          <p className={`mt-2.5 ${panelNote}`}>
            Copy it now. For security the link can’t be shown again, but you can
            make a new one at any time.
          </p>
          <div className={actions}>
            <button
              type="button"
              className={secondaryButton}
              onClick={turnOff}
              disabled={busy}
            >
              Turn off link
            </button>
            <button type="button" className={primaryButton} onClick={copy}>
              <FiCopy aria-hidden="true" /> Copy link
            </button>
          </div>
        </>
      ) : status.isError ? (
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <p className={panelNote}>The link’s status couldn’t be loaded.</p>
          <button
            type="button"
            className={retryButton}
            onClick={status.refetch}
          >
            Try again
          </button>
        </div>
      ) : status.isFetching || !link ? (
        <p className={`mt-5 ${panelNote}`} role="status">
          Checking the link…
        </p>
      ) : link.active ? (
        <>
          <p className="mt-5 rounded-2xl bg-spot-violet/[.06] px-4 py-3.5 text-[13.5px] leading-[1.6] text-spot-body">
            A link has been on since {moment(link.createdAt)}.{" "}
            {link.viewCount
              ? `Viewed ${link.viewCount} ${link.viewCount === 1 ? "time" : "times"}, last on ${moment(link.lastViewedAt)}.`
              : "Nobody has opened it yet."}
          </p>
          <p className={`mt-2.5 ${panelNote}`}>
            Lost the link? Make a new one, and the old one stops working.
          </p>
          <div className={actions}>
            <button
              type="button"
              className={secondaryButton}
              onClick={turnOff}
              disabled={busy}
            >
              Turn off link
            </button>
            <button
              type="button"
              className={primaryButton}
              onClick={makeLink}
              disabled={busy}
              autoFocus
            >
              <FiRefreshCw aria-hidden="true" />
              {creating ? "Making the link…" : "Make a new link"}
            </button>
          </div>
        </>
      ) : (
        <div className={actions}>
          <button
            type="button"
            className={primaryButton}
            onClick={makeLink}
            disabled={busy}
            autoFocus
          >
            <FiLink aria-hidden="true" />
            {creating ? "Making the link…" : "Create link"}
          </button>
        </div>
      )}
    </Dialog>
  );
};

export default ShareDialog;
