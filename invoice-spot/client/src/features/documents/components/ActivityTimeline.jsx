import {
  FiBell,
  FiCheckCircle,
  FiCheckSquare,
  FiCircle,
  FiCopy,
  FiEye,
  FiLink,
  FiLink2,
  FiDollarSign,
  FiEdit2,
  FiFileMinus,
  FiFilePlus,
  FiRepeat,
  FiRotateCcw,
  FiSend,
  FiSlash,
  FiTrash2,
  FiXCircle,
} from "react-icons/fi";
import { useGetDocumentActivityQuery } from "../documentsApiSlice";
import { useBusiness } from "../../business/permissions";
import { panelNote, retryButton } from "../../../components/workspace/pastel";

// How each event reads. The server words the detail when the event happens,
// so an old entry never changes when the document does; a copy made as a
// draft had no number to name.
const EVENTS = {
  CREATED: { icon: FiFilePlus, text: () => "Created" },
  ISSUED: {
    icon: FiCheckSquare,
    text: (detail) => (detail ? `Issued as ${detail}` : "Issued"),
  },
  EDITED: { icon: FiEdit2, text: () => "Edited" },
  EMAILED: { icon: FiSend, text: (detail) => `Emailed ${detail}` },
  PAYMENT_RECORDED: {
    icon: FiDollarSign,
    text: (detail) => `Payment recorded: ${detail}`,
  },
  PAYMENT_EDITED: {
    icon: FiEdit2,
    text: (detail) => `Payment changed to ${detail}`,
  },
  PAYMENT_DELETED: {
    icon: FiTrash2,
    text: (detail) => `Payment removed: ${detail}`,
  },
  COPIED_FROM: {
    icon: FiCopy,
    text: (detail) => `Copied from ${detail || "a draft"}`,
  },
  COPIED_TO: {
    icon: FiCopy,
    text: (detail) =>
      detail ? `Copied to ${detail}` : "Copied to a new draft",
  },
  VOIDED: { icon: FiSlash, text: (detail) => `Voided: ${detail}` },
  CREDITED: { icon: FiFileMinus, text: (detail) => `Credited: ${detail}` },
  CREDIT_VOIDED: {
    icon: FiSlash,
    text: (detail) => `Credit note ${detail} voided`,
  },
  REFUND_RECORDED: {
    icon: FiRotateCcw,
    text: (detail) => `Refund recorded: ${detail}`,
  },
  REFUND_EDITED: {
    icon: FiEdit2,
    text: (detail) => `Refund changed to ${detail}`,
  },
  REFUND_DELETED: {
    icon: FiTrash2,
    text: (detail) => `Refund removed: ${detail}`,
  },
  CONVERTED_FROM: {
    icon: FiRepeat,
    text: (detail) => `Created from quotation ${detail}`,
  },
  CONVERTED_TO: {
    icon: FiRepeat,
    text: (detail) =>
      detail
        ? `Converted to invoice ${detail}`
        : "Converted to a draft invoice",
  },
  RECEIPT_EMAILED: {
    icon: FiSend,
    text: (detail) => `Emailed receipt ${detail}`,
  },
  // A link made to send in a message says so: "for an email".
  SHARED: {
    icon: FiLink,
    text: (detail) =>
      detail ? `Share link created ${detail}` : "Share link created",
  },
  SHARE_REVOKED: { icon: FiLink2, text: () => "Share link turned off" },
  VIEWED: { icon: FiEye, text: () => "Viewed by the client" },
  ACCEPTED: { icon: FiCheckCircle, text: () => "Accepted by the client" },
  DECLINED: {
    icon: FiXCircle,
    text: (detail) =>
      detail ? `Declined by the client: ${detail}` : "Declined by the client",
  },
  REMINDED: { icon: FiBell, text: (detail) => `Reminder sent ${detail}` },
  PAYMENT_REPORTED: {
    icon: FiDollarSign,
    text: (detail) => `Payment reported by the client: ${detail}`,
  },
  PAYMENT_REPORT_CONFIRMED: {
    icon: FiCheckCircle,
    text: (detail) => `Reported payment confirmed: ${detail}`,
  },
  PAYMENT_REPORT_DISMISSED: {
    icon: FiXCircle,
    text: (detail) => `Reported payment dismissed: ${detail}`,
  },
  // The detail names the template and how often it repeats: "INV-1 · monthly".
  RECURRING: {
    icon: FiRepeat,
    text: (detail) => {
      const [template, frequency] = (detail || "").split(" · ");
      return `Made by the ${frequency} schedule of ${template}`;
    },
  },
};

// A time zone as the browser names it, or none when it is missing or one
// the browser doesn't know.
const knownZone = (timeZone) => {
  if (!timeZone) return undefined;
  try {
    return new Intl.DateTimeFormat("en", { timeZone }).resolvedOptions()
      .timeZone;
  } catch {
    return undefined;
  }
};

// When something happened, in the business's time zone, as every other date
// on the page is, with the zone's short name, such as "PDT", when the
// viewer's own zone is another. Without a zone it is the viewer's.
export const moment = (value, timeZone) => {
  const zone = knownZone(timeZone);
  const viewer = new Intl.DateTimeFormat("en").resolvedOptions().timeZone;
  return new Date(value).toLocaleString("en", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone: zone,
    ...(zone && zone !== viewer && { timeZoneName: "short" }),
  });
};

// Who did it, when a member did. The client's views, automatic runs and
// events from before this was kept name no one rather than a guess.
const byline = (event) =>
  event.byYou ? "by you" : event.actor ? `by ${event.actor}` : null;

const ActivityTimeline = ({ documentId }) => {
  const query = useGetDocumentActivityQuery(documentId);
  const timeZone = useBusiness().business?.timeZone;
  const events = query.data?.events || [];
  if (query.isLoading) {
    return (
      <p className={panelNote} role="status">
        Loading activity…
      </p>
    );
  }
  if (query.isError) {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p className={panelNote}>The activity couldn’t be loaded.</p>
        <button type="button" className={retryButton} onClick={query.refetch}>
          Try again
        </button>
      </div>
    );
  }
  return (
    <ol className="m-0 flex list-none flex-col p-0">
      {events.map((event, index) => {
        const { icon: Icon, text } = EVENTS[event.type] || {
          icon: FiCircle,
          text: (detail) => detail || event.type,
        };
        return (
          <li
            key={`${event.at}-${index}`}
            className="relative flex gap-3.5 pb-5 last:pb-0"
          >
            {index < events.length - 1 && (
              <span
                className="absolute bottom-0 left-[17px] top-10 w-px bg-[#23273517]"
                aria-hidden="true"
              />
            )}
            <span
              className="grid size-9 shrink-0 place-items-center rounded-xl bg-spot-violet/[.07] text-spot-violet [&>svg]:size-4 [&>svg]:stroke-[1.7]"
              aria-hidden="true"
            >
              <Icon />
            </span>
            <div className="min-w-0 pt-0.5">
              <p className="text-[14px] text-spot-ink [overflow-wrap:anywhere]">
                {text(event.detail)}
              </p>
              <p className="mt-0.5 text-[13px] text-spot-muted [overflow-wrap:anywhere]">
                <time dateTime={event.at}>{moment(event.at, timeZone)}</time>
                {byline(event) && ` · ${byline(event)}`}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
};

export default ActivityTimeline;
