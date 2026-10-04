import { Link } from "react-router-dom";
import {
  FiAlertCircle,
  FiCalendar,
  FiCheckCircle,
  FiChevronRight,
  FiEdit3,
  FiFileText,
  FiRepeat,
} from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { overdueLink } from "../../documents/listLinks";
import { attentionFooter, attentionRow } from "../dashboardModel";
import { overviewPanel, panelLink } from "./DashboardPanels";

// Each kind's icon and tint; the words beside them carry the meaning.
const KINDS = {
  paymentReport: [FiCheckCircle, "bg-avatar-sage text-avatar-sage-ink"],
  overdue: [FiAlertCircle, "bg-overdue text-overdue-icon"],
  dueSoon: [FiCalendar, "bg-avatar-blue text-card-blue-icon"],
  quotation: [FiFileText, "bg-avatar-lilac text-avatar-lilac-ink"],
  recurring: [FiRepeat, "bg-avatar-blue text-card-blue-icon"],
  draft: [FiEdit3, "bg-[#2327350d] text-spot-body"],
};

// A row is one line on a wide panel. Below 600px the kind is left to the
// icon and the state (and to screen readers), and the state goes under the
// number and client; below 340px the client gets a line of its own. The
// container ranges meet without overlapping, since their order in the
// stylesheet is not fixed, and without a gap, since a container can be a
// fraction of a pixel wide.
const row =
  "-mx-2 grid items-center gap-x-3.5 gap-y-0.5 rounded-xl px-2 py-[9px] text-spot-ink transition-colors [grid-template-areas:'icon_kind_client_state_amount'] [grid-template-columns:34px_120px_minmax(0,1fr)_minmax(150px,0.6fr)_minmax(84px,auto)] hover:bg-white/50 [@container_attention_(340px<=width<600px)]:[grid-template-areas:'icon_kind_client_amount'_'icon_state_state_amount'] [@container_attention_(340px<=width<600px)]:[grid-template-columns:34px_auto_minmax(0,1fr)_auto] [@container_attention_(width<340px)]:[grid-template-areas:'icon_kind_amount'_'icon_client_client'_'icon_state_state'] [@container_attention_(width<340px)]:[grid-template-columns:34px_minmax(0,1fr)_auto]";

// The few things that need doing now, most urgent first, each opening its
// document. The footer names what there is none of, and what the list
// leaves out. Overdue invoices opens them all, in the same currency when
// one was chosen.
const AttentionPanel = ({ snapshot, currencyChosen }) => {
  const { attention, currency } = snapshot;
  const items = attention?.items || [];
  const footer = attentionFooter(attention?.counts, items);
  return (
    <section
      className={`${overviewPanel} [--rise-delay:240ms]`}
      aria-labelledby="attention-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="attention-title" className={sectionTitle}>
          Needs your attention
        </h2>
        {attention?.counts?.overdue > 0 && (
          <Link
            to={overdueLink(currencyChosen ? currency : undefined)}
            className={panelLink}
          >
            Overdue invoices
            <FiChevronRight aria-hidden="true" />
          </Link>
        )}
      </div>
      {items.length ? (
        <>
          <ol className="m-0 list-none p-0 [container-name:attention] [container-type:inline-size]">
            {items.map((item, index) => {
              const { label, state, when, to } = attentionRow(item);
              const [Icon, tint] = KINDS[item.kind] || KINDS.draft;
              return (
                <li
                  className={index ? "border-t border-[#23273512]" : ""}
                  key={`${item.kind}-${item.documentId}-${index}`}
                >
                  <Link to={to} className={row}>
                    <span
                      className={`grid size-[34px] place-items-center rounded-xl [grid-area:icon] [&>svg]:size-4 [&>svg]:stroke-[1.6] ${tint}`}
                      aria-hidden="true"
                    >
                      <Icon />
                    </span>
                    <span className="flex min-w-0 flex-col [grid-area:kind]">
                      <span className="text-[12px] text-spot-muted [@container_attention_(width<600px)]:sr-only">
                        {label}
                      </span>
                      <span className="truncate text-[14px] font-medium">
                        {item.documentNumber || "No number yet"}
                      </span>
                    </span>
                    <span className="min-w-0 truncate text-[14px] [grid-area:client]">
                      {item.client || "No client"}
                    </span>
                    <span className="flex min-w-0 flex-col [grid-area:state] [@container_attention_(width<600px)]:flex-row [@container_attention_(width<600px)]:flex-wrap [@container_attention_(width<600px)]:items-baseline [@container_attention_(width<600px)]:gap-x-2">
                      {state && <span className="text-[13px]">{state}</span>}
                      {when && (
                        <span className="text-[12px] text-spot-muted">
                          {when}
                        </span>
                      )}
                    </span>
                    <Amount
                      value={item.amount}
                      currency={currency}
                      hideCode
                      className="justify-self-end text-[17px] font-light tracking-[-0.03em] [grid-area:amount]"
                    />
                  </Link>
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
