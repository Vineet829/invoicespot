import { Link } from "react-router-dom";
import { FiChevronRight } from "react-icons/fi";
import { Amount } from "../../../components/workspace/Workspace";
import { sectionTitle } from "../../../components/workspace/pastel";
import { countOf } from "../../../utils/counts";
import {
  findPeriod,
  formatMoney,
  salesByCustomerLink,
  topClientRows,
} from "../dashboardModel";
import { overviewPanel, panelLink } from "./DashboardPanels";

// A row: the client's name, its bar, and what it owes then its count of
// documents, which starts a new line with its dot when the row is narrow;
// then what was billed, which can widen the column.
const row =
  "relative -mx-2 grid grid-cols-[minmax(0,1fr)_minmax(96px,auto)] items-center gap-2.5 rounded-xl px-2 py-[7px]";
// A client's name is a link that covers the whole row, and the row shows the
// link's focus. The name reads the whole row, so the words beside it are
// hidden from screen readers rather than read twice.
const nameLink =
  "truncate text-spot-night underline decoration-spot-night/30 underline-offset-[3px] after:absolute after:inset-0 after:rounded-xl after:content-[''] hover:decoration-current focus-visible:outline-none";
const linkedRow =
  "transition-colors hover:bg-white/50 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[#5b47a0]";

// The clients billed the most in the period shown, in the overview's
// currency, with what each owes now, in that currency too. A name opens the
// client; Sales by customer opens every client's sales on the Clients tab,
// for the same period, in the same currency when one was chosen.
const TopClientsPanel = ({ snapshot, currencyChosen }) => {
  const { currency } = snapshot;
  const period = findPeriod(snapshot.period.key);
  const rows = topClientRows(snapshot.topClients);
  return (
    <section
      className={`${overviewPanel} [--rise-delay:240ms]`}
      aria-labelledby="top-clients-title"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="top-clients-title" className={sectionTitle}>
          Top clients
        </h2>
        <Link
          to={salesByCustomerLink(
            period.key,
            currencyChosen ? currency : undefined,
          )}
          className={panelLink}
        >
          Sales by customer
          <FiChevronRight aria-hidden="true" />
        </Link>
      </div>
      {rows.length ? (
        <>
          <p className="text-[12.5px] text-spot-muted">
            Billed {period.inThese}, largest first
          </p>
          <ol className="m-0 list-none p-0">
            {rows.map((entry, index) => {
              const name = entry.client || "No client";
              const owes =
                Number(entry.owed) > 0
                  ? `Owes ${formatMoney(entry.owed, currency)}`
                  : "Owes nothing";
              const documents = countOf(
                entry.documents,
                "document",
                "documents",
              );
              const label = `${name}: ${formatMoney(entry.billed, currency)} billed on ${documents}, ${owes.toLowerCase()}`;
              const hidden = entry.to ? "true" : undefined;
              return (
                <li
                  className={index ? "border-t border-[#23273512]" : ""}
                  key={entry.clientId || `name-${entry.client}`}
                >
                  <div
                    className={`${row} ${entry.to ? linkedRow : ""}`}
                    role={entry.to ? undefined : "img"}
                    aria-label={entry.to ? undefined : label}
                  >
                    <span className="flex min-w-0 flex-col gap-1">
                      <span className="flex min-w-0 text-[13.5px]">
                        {entry.to ? (
                          <Link
                            to={entry.to}
                            className={nameLink}
                            aria-label={`${label}. View the client`}
                          >
                            {name}
                          </Link>
                        ) : (
                          <span className="truncate">{name}</span>
                        )}
                      </span>
                      <span
                        className="block h-1.5 overflow-hidden rounded-full bg-spot-blue/[.14]"
                        aria-hidden="true"
                      >
                        <span
                          className="block h-full origin-left animate-ov-bar rounded-[inherit] bg-spot-blue"
                          style={{ width: `${entry.bar * 100}%` }}
                        />
                      </span>
                      <span
                        className={`text-[11.5px] ${Number(entry.owed) > 0 ? "text-spot-ink" : "text-spot-muted"}`}
                        aria-hidden={hidden}
                      >
                        {owes}{" "}
                        <span className="whitespace-nowrap">· {documents}</span>
                      </span>
                    </span>
                    <span className="justify-self-end" aria-hidden={hidden}>
                      <Amount
                        value={entry.billed}
                        currency={currency}
                        hideCode
                        className="text-[15px] font-light tracking-[-0.03em]"
                      />
                    </span>
                  </div>
                </li>
              );
            })}
          </ol>
          {rows.some((entry) => entry.to) && (
            <p className="mt-auto border-t border-[#23273512] pt-2 text-[12px] text-spot-muted">
              A name opens the client: their documents, balance and statement.
            </p>
          )}
        </>
      ) : (
        <div className="flex min-h-[200px] flex-1 flex-col items-center justify-center gap-3 rounded-[18px] bg-white/50 p-3 text-center">
          <span
            className="size-11 rounded-full border-[7px] border-dashed border-[#23273524]"
            aria-hidden="true"
          />
          <p className="text-[13.5px] text-spot-body">
            No one billed {period.inThese}.
          </p>
        </div>
      )}
    </section>
  );
};

export default TopClientsPanel;
