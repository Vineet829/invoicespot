import { useState } from "react";
import {
  FiCopy,
  FiLink,
  FiMail,
  FiMessageCircle,
  FiRefreshCw,
} from "react-icons/fi";
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
  useCreatePortalLinkMutation,
  useEmailPortalMutation,
  useGetPortalEmailDraftQuery,
  useGetPortalStatusQuery,
  useRevokePortalLinkMutation,
  useSharePortalOnWhatsAppMutation,
} from "../customersApiSlice";
import { EmailDialog } from "../../documents/components/EmailDocumentDialog";

const moment = (value) =>
  new Date(value).toLocaleString("en", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

const actions = "mt-6 flex flex-wrap justify-end gap-2.5";

// A client's portal link: one link to all their documents, balances and
// statement, which can be emailed or sent on WhatsApp. A new link replaces
// the old one. Like a share link, it is kept sealed to be shown again when
// the server has an encryption key.
const PortalDialog = ({ customer, open, onClose, demoNote }) => {
  const status = useGetPortalStatusQuery(customer._id, { skip: !open });
  const [create, { isLoading: creating }] = useCreatePortalLinkMutation();
  const [revoke, { isLoading: revoking }] = useRevokePortalLinkMutation();
  const [emailing, setEmailing] = useState(false);
  const draft = useGetPortalEmailDraftQuery(customer._id, { skip: !emailing });
  const [email, { isLoading: sending }] = useEmailPortalMutation();
  const [whatsApp, { isLoading: messaging }] =
    useSharePortalOnWhatsAppMutation();
  const [made, setMade] = useState(null);
  const busy = creating || revoking || messaging;
  const close = () => {
    setMade(null);
    onClose();
  };
  const makeLink = async () => {
    try {
      setMade(await create(customer._id).unwrap());
    } catch (error) {
      toast.error(errorMessage(error, "The link could not be created."));
    }
  };
  const turnOff = async () => {
    try {
      const result = await revoke(customer._id).unwrap();
      setMade(null);
      toast.success(result.message || "The portal link was turned off.");
    } catch (error) {
      toast.error(errorMessage(error, "The link could not be turned off."));
    }
  };
  const sendEmail = async (form) => {
    try {
      const result = await email({ id: customer._id, ...form }).unwrap();
      toast.success(result.message || "The portal link was emailed.");
      setMade(null);
      setEmailing(false);
    } catch (error) {
      toast.error(errorMessage(error, "The portal link could not be emailed."));
    }
  };
  // The tab opens at once, while the click still counts, so no pop-up
  // blocker stops it; it goes to WhatsApp once the server has the link.
  const sendOnWhatsApp = async () => {
    const tab = window.open("", "_blank");
    if (tab) tab.opener = null;
    try {
      const result = await whatsApp(customer._id).unwrap();
      setMade(null);
      if (tab) tab.location.href = result.url;
      else window.location.assign(result.url);
      if (result.linkReplaced) {
        toast.info(
          "This replaced the client’s earlier portal link, which no longer works.",
        );
      }
    } catch (error) {
      tab?.close();
      toast.error(
        errorMessage(error, "The WhatsApp message could not be prepared."),
      );
    }
  };
  const copy = (url) => async () => {
    try {
      await navigator.clipboard.writeText(url);
      toast.success("Link copied.");
    } catch {
      toast.error("The link couldn’t be copied. Select it in the box instead.");
    }
  };
  const link = status.data;
  const turnOffButton = (
    <button
      type="button"
      className={secondaryButton}
      onClick={turnOff}
      disabled={busy}
    >
      Turn off link
    </button>
  );
  const shownLink = (url, note) => (
    <>
      <label className="mt-5 block">
        <span className="sr-only">Portal link</span>
        <input
          className={inputControl}
          readOnly
          value={url}
          autoFocus
          onFocus={(event) => event.target.select()}
        />
      </label>
      <p className={`mt-2.5 ${panelNote}`}>{note}</p>
    </>
  );
  return (
    <Dialog
      open={open}
      title="Client portal"
      onClose={close}
      busy={busy}
      size="medium"
    >
      <p className="mt-3 text-[14px] leading-[1.6] text-spot-body">
        One link where {customer.name} sees every document you issued them, what
        they owe and their statement. They can accept quotations and report
        payments there. No sign-in is needed.
      </p>
      {made ? (
        <>
          {shownLink(
            made.url,
            made.kept
              ? "You can come back here for the link at any time."
              : "Copy it now. For security the link can’t be shown again, but you can make a new one at any time.",
          )}
          <div className={actions}>
            {turnOffButton}
            <button
              type="button"
              className={primaryButton}
              onClick={copy(made.url)}
            >
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
              ? `Opened ${link.viewCount} ${link.viewCount === 1 ? "time" : "times"}, last on ${moment(link.lastViewedAt)}.`
              : "Nobody has opened it yet."}
          </p>
          {link.url ? (
            shownLink(
              link.url,
              "If it reached someone it shouldn’t have, make a new one and this one stops working.",
            )
          ) : (
            <p className={`mt-2.5 ${panelNote}`}>
              This link can’t be shown again. Lost it? Make a new one, and the
              old one stops working.
            </p>
          )}
          <div className={actions}>
            {turnOffButton}
            <button
              type="button"
              className={link.url ? secondaryButton : primaryButton}
              onClick={makeLink}
              disabled={busy}
              autoFocus={!link.url}
            >
              <FiRefreshCw aria-hidden="true" />
              {creating ? "Making the link…" : "Make a new link"}
            </button>
            {link.url && (
              <button
                type="button"
                className={primaryButton}
                onClick={copy(link.url)}
              >
                <FiCopy aria-hidden="true" /> Copy link
              </button>
            )}
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
      {!status.isError && (link || made) && (
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-[#23273512] pt-5">
          <p className={panelNote}>Or send them the link.</p>
          <div className="flex flex-wrap gap-2.5">
            <button
              type="button"
              className={secondaryButton}
              onClick={() => setEmailing(true)}
              disabled={busy || Boolean(demoNote)}
              title={demoNote}
            >
              <FiMail aria-hidden="true" /> Email the link
            </button>
            <button
              type="button"
              className={secondaryButton}
              onClick={sendOnWhatsApp}
              disabled={busy}
            >
              <FiMessageCircle aria-hidden="true" />
              {messaging ? "Opening WhatsApp…" : "Share on WhatsApp"}
            </button>
          </div>
        </div>
      )}
      <EmailDialog
        title="Email the portal link"
        open={emailing}
        onClose={() => setEmailing(false)}
        draft={draft}
        sending={sending}
        onSend={sendEmail}
      />
    </Dialog>
  );
};

export default PortalDialog;
