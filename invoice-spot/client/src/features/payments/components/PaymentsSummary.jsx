import { Link } from "react-router-dom";
import { FiAlertCircle } from "react-icons/fi";
import {
  Amount,
  DataTable,
  Panel,
  ShareBar,
} from "../../../components/workspace/Workspace";
import {
  panel,
  panelNote,
  skeleton,
  textLink,
} from "../../../components/workspace/pastel";
import { countOf } from "../../../utils/counts";
import { dateSpan } from "../../../utils/dates";
import SummaryFigure from "../../documents/components/SummaryFigure";
import { share } from "../../reports/reportModel";

const NO_ROWS = [];
const partTitle = "mb-1 text-[14px] font-medium text-spot-ink";

// While the figures load, blocks keep the summary's room, so the list below
// stays where it is when they arrive.
const Placeholder = () => (
  <div className={panel} aria-hidden="true">
    <div className="flex flex-col gap-2">
      <span className={`${skeleton} h-[13px] w-[70px] rounded-md`} />
      <span className={`${skeleton} h-9 w-[260px] rounded-md`} />
      <span className={`${skeleton} h-[22px] w-[220px] rounded-full`} />
    </div>
    <div className="mt-6 grid min-w-0 gap-x-10 gap-y-6 compact:mt-5 min-[1280px]:grid-cols-2">
      {[0, 1].map((side) => (
        <div className="flex min-w-0 flex-col gap-3" key={side}>
          <span className={`${skeleton} h-3.5 w-[130px] rounded-md`} />
          {Array.from({ length: 4 }, (_, index) => (
            <span className={`${skeleton} h-8 w-full rounded-md`} key={index} />
          ))}
        </div>
      ))}
    </div>
  </div>
);

// A table of what each method, or each client, brought in, with a bar
// against the largest.
const Breakdown = ({ title, rows, name, rowKey, tone, currency, several }) => {
  const largest = Math.max(0, ...rows.map((row) => Number(row.amount)));
  return (
    <div className="min-w-0">
      <h3 className={partTitle}>{title}</h3>
      <DataTable
        label={`${title}, ${currency}`}
        narrow
        rows={rows}
        rowKey={rowKey}
        columns={[
          {
            label: name.label,
            type: "main",
            render: (row) => (
              <>
                {name.render(row)}
                <ShareBar ratio={share(row.amount, largest)} tone={tone} />
              </>
            ),
          },
          {
            label: "Payments",
            type: "number",
            render: (row) => Number(row.payments).toLocaleString("en"),
          },
          {
            label: "Amount",
            type: "amount",
            render: (row) => (
              <Amount
                value={row.amount}
                currency={currency}
                hideCode={!several}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

// What came in over the Payments list's period, in its currency or the
// business's first: the total against the period before when there is one,
// then by method, receipts included, and the clients who paid the most. It
// follows the list's period and currency, but not its search or method, so
// the figures stay those of the whole period while the list narrows.
const PaymentsSummary = ({ query }) => {
  const summary = query.data;
  if (!summary) {
    return query.isError && !query.isFetching ? (
      <p
        className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl border border-white/90 bg-white/60 px-4 py-2.5 text-[13px] text-spot-body [&>svg]:size-4 [&>svg]:shrink-0 [&>svg]:text-overdue-icon"
        role="alert"
      >
        <FiAlertCircle aria-hidden="true" />
        We couldn’t sum up these payments.
        <button
          type="button"
          className={`${textLink} bg-transparent p-0 text-[13px] coarse:min-h-11`}
          onClick={query.refetch}
        >
          Try again
        </button>
      </p>
    ) : (
      <Placeholder />
    );
  }
  const { currency } = summary;
  const several = summary.currencies?.length > 1;
  const days =
    summary.period?.from && summary.period?.to
      ? `, ${dateSpan(summary.period.from, summary.period.to)}`
      : "";
  const methods = summary.methods || NO_ROWS;
  const payers = summary.payers || NO_ROWS;
  return (
    <Panel>
      <h2 className="sr-only">What came in</h2>
      <div
        className={`transition-opacity ${query.isFetching ? "opacity-60" : ""}`}
        aria-busy={query.isFetching || undefined}
      >
        <SummaryFigure
          label="Received"
          total={summary.total}
          currency={currency}
          several={several}
          detail={`from ${countOf(summary.payments, "payment", "payments")}${days}`}
          period={summary.period}
          previousTotal={summary.previousTotal}
        />
        {methods.length || payers.length ? (
          <div className="mt-6 grid min-w-0 gap-x-10 gap-y-6 compact:mt-5 min-[1280px]:grid-cols-2">
            <Breakdown
              title="Received by method"
              rows={methods}
              rowKey={(row) => row.method}
              name={{ label: "Method", render: (row) => row.method }}
              tone="bg-card-lilac-bar"
              currency={currency}
              several={several}
            />
            <Breakdown
              title="Who paid most"
              rows={payers}
              rowKey={(row) => row.clientId || `name-${row.client}`}
              name={{
                label: "Client",
                render: (row) =>
                  row.clientId ? (
                    <Link
                      className={textLink}
                      to={`/single-customer/${row.clientId}`}
                    >
                      {row.client}
                    </Link>
                  ) : (
                    row.client || "No client"
                  ),
              }}
              tone="bg-card-lilac-bar"
              currency={currency}
              several={several}
            />
          </div>
        ) : (
          <p className={`mt-3 ${panelNote}`}>
            {days
              ? "Nothing came in over this period."
              : "Nothing has come in yet."}
          </p>
        )}
      </div>
    </Panel>
  );
};

export default PaymentsSummary;
