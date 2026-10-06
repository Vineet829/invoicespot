import { Link } from "react-router-dom";
import {
  FiAlertCircle,
  FiCalendar,
  FiCheckCircle,
  FiEdit3,
} from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import {
  attentionFooter,
  attentionItems,
  attentionRow,
  formatMoney,
} from "../dashboardModel";
import { overviewPanel } from "./DashboardPanels";

// Each kind's icon and tint; the words beside them carry the meaning.
const KINDS = {
  paymentReport: [FiCheckCircle, "bg-avatar-sage text-avatar-sage-ink"],
  overdue: [FiAlertCircle, "bg-overdue text-overdue-icon"],
  dueSoon: [FiCalendar, "bg-avatar-blue text-card-blue-icon"],
  draft: [FiEdit3, "bg-[#2327350d] text-spot-body"],
};

// A row is one line on a wide panel. Below 600px the kind is left to the
// icon and the state (and to screen readers), and the state goes under the
// number and client; below 340px the client gets a line of its own. The
// container ranges meet without overlapping, since their order in the
// stylesheet is not fixed, and without a gap, since a container can be a
// fraction of a pixel wide.
const row =
  "relative -mx-2 grid items-center gap-x-3.5 gap-y-0.5 rounded-xl px-2 py-[9px] text-spot-ink transition-colors [grid-template-areas:'icon_kind_client_state_amount'] [grid-template-columns:34px_120px_minmax(0,1fr)_minmax(150px,0.6fr)_minmax(84px,auto)] [@container_attention_(340px<=width<600px)]:[grid-template-areas:'icon_kind_client_amount'_'icon_state_state_amount'] [@container_attention_(340px<=width<600px)]:[grid-template-columns:34px_auto_minmax(0,1fr)_auto] [@container_attention_(width<340px)]:[grid-template-areas:'icon_kind_amount'_'icon_client_client'_'icon_state_state'] [@container_attention_(width<340px)]:[grid-template-columns:34px_minmax(0,1fr)_auto]";
// The row's own link, to its document, covers the whole row and shows its
// focus around it. Its name reads the whole row, so the words beside it are
// hidden from screen readers rather than read twice.
const documentLink =
  "flex min-w-0 flex-col [grid-area:kind] after:absolute after:inset-0 after:rounded-xl after:content-[''] focus-visible:outline-none focus-visible:after:outline focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[#5b47a0]";

// The few invoices that need doing now, and drafts to issue, most urgent
// first, each opening its document. Quotations and recurring invoices are
// in Coming up, the client is one click away on the document, and the
// Overdue figure above opens every overdue invoice, so nothing here opens
// what another panel opens. A document listed twice, such as an overdue
// invoice with a payment reported on it, opens from its first row only. The
// note names what there is none of, and what the list leaves out.
const AttentionPanel = ({ snapshot }) => {
  const { attention, currency } = snapshot;
  const items = attentionItems(attention);
  const footer = attentionFooter(attention?.counts, items);
  const opened = new Set();
  return (
    <section
      className={`${overviewPanel} [--rise-delay:240ms]`}
      aria-labelledby="attention-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="attention-title" className={sectionTitle}>
          Needs your attention
        </h2>
      </div>
      {items.length ? (
        <>
          <ol className="m-0 list-none p-0 [container-name:attention] [container-type:inline-size]">
            {items.map((item, index) => {
              const { label, state, when, to } = attentionRow(item);
              const [Icon, tint] = KINDS[item.kind];
              const number = item.documentNumber || "No number yet";
              const client = item.client || "No client";
              const name = [
                `${label} ${number}`.trim(),
                client,
                state,
                when,
                formatMoney(item.amount, currency),
              ]
                .filter(Boolean)
                .join(", ");
              const opens = !opened.has(item.documentId);
              opened.add(item.documentId);
              const kind = (
                <>
                  <span className="text-[12px] text-spot-muted [@container_attention_(width<600px)]:sr-only">
                    {label}
                  </span>
                  <span className="truncate text-[14px] font-medium">
                    {number}
                  </span>
                </>
              );
              return (
                <li
                  className={index ? "border-t border-[#23273512]" : ""}
                  key={`${item.kind}-${item.documentId}-${index}`}
                >
                  <div
                    className={`${row} ${opens ? "hover:bg-white/50" : ""}`}
                    role={opens ? undefined : "img"}
                    aria-label={opens ? undefined : name}
                  >
                    <span
                      className={`grid size-[34px] place-items-center rounded-xl [grid-area:icon] [&>svg]:size-4 [&>svg]:stroke-[1.6] ${tint}`}
                      aria-hidden="true"
                    >
                      <Icon />
                    </span>
                    {opens ? (
                      <Link to={to} className={documentLink} aria-label={name}>
                        {kind}
                      </Link>
                    ) : (
                      <span className="flex min-w-0 flex-col [grid-area:kind]">
                        {kind}
                      </span>
                    )}
                    <span
                      className="min-w-0 truncate text-[14px] [grid-area:client]"
                      aria-hidden="true"
                    >
                      {client}
                    </span>
                    <span
                      className="flex min-w-0 flex-col [grid-area:state] [@container_attention_(width<600px)]:flex-row [@container_attention_(width<600px)]:flex-wrap [@container_attention_(width<600px)]:items-baseline [@container_attention_(width<600px)]:gap-x-2"
                      aria-hidden="true"
                    >
                      {state && <span className="text-[13px]">{state}</span>}
                      {when && (
                        <span className="text-[12px] text-spot-muted">
                          {when}
                        </span>
                      )}
                    </span>
                    <span
                      className="justify-self-end [grid-area:amount]"
                      aria-hidden="true"
                    >
                      <Amount
                        value={item.amount}
                        currency={currency}
                        hideCode
                        className="text-[17px] font-light tracking-[-0.03em]"
                      />
                    </span>
                  </div>
                </li>
              );
            })}
          </ol>
          {footer && (
            <p className="mt-auto border-t border-[#23273512] pt-2 text-[12px] text-spot-muted">
              {footer}
            </p>
          )}
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-10 text-center">
          <span
            className="grid size-11 place-items-center rounded-2xl bg-avatar-sage text-avatar-sage-ink [&>svg]:size-5 [&>svg]:stroke-[1.6]"
            aria-hidden="true"
          >
            <FiCheckCircle />
          </span>
          <p className="font-display text-[17px] font-light">
            Nothing needs your attention right now.
          </p>
        </div>
      )}
    </section>
  );
};

export default AttentionPanel;
