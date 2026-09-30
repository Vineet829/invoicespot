import { useEffect, useRef, useState } from "react";
import {
  FiArrowDownLeft,
  FiChevronLeft,
  FiChevronRight,
  FiChevronsLeft,
  FiChevronsRight,
} from "react-icons/fi";
import { STATUS_LABELS, formatDate, formatMoney } from "../../dashboardModel";
import {
  HoverCard,
  historyHeading,
  historyPanel,
  useHoverCard,
} from "../../components/DashboardPanels";
import {
  hoverLabel,
  hoverValue,
  pageSizeSelect,
  pagerButton,
  panelNote,
  sectionTitle,
} from "../../../../components/workspace/pastel";

const AVATAR_TONES = [
  "bg-avatar-lilac text-avatar-lilac-ink",
  "bg-avatar-blue text-avatar-blue-ink",
  "bg-avatar-peach text-avatar-peach-ink",
  "bg-avatar-sage text-avatar-sage-ink",
];
const cell =
  "p-0 pr-3.5 font-normal [overflow-wrap:anywhere] last:pr-0 last:text-right";
// On phones each row becomes a small card with labelled fields.
const mobileField =
  "hidden max-[700px]:mb-1 max-[700px]:block max-[700px]:text-[11px] max-[700px]:text-spot-muted";

const PaymentAmount = ({ amount, currency }) => {
  const formatted = formatMoney(amount, currency);
  return (
    <span
      className="font-display font-light tracking-[-0.03em] [font-size:min(19px,calc(130px_/_(var(--chars)_*_0.57)))] [@container_(min-width:650px)]:[font-size:min(20px,calc(130px_/_(var(--chars)_*_0.57)))]"
      style={{ "--chars": formatted.length }}
    >
      {formatted.slice(0, -3)}
      <span className="text-[0.7em] text-spot-faint">
        {formatted.slice(-3)}
      </span>
    </span>
  );
};

// The document a payment was recorded against: its type and number, total,
// status and due date. Shown when a row is hovered.
const PaymentDocument = ({ document, currency }) => (
  <>
    <span className={hoverLabel}>{document.documentType || "Document"}</span>
    <strong className={hoverValue}>{document.documentNumber || "—"}</strong>
    <dl className="mt-2.5 grid gap-1">
      {[
        ["Document total", formatMoney(document.total, currency)],
        ["Status", STATUS_LABELS[document.status] || document.status || "—"],
        ["Due date", formatDate(document.dueDate)],
      ].map(([term, value]) => (
        <div className="flex justify-between gap-[18px]" key={term}>
          <dt className="text-spot-muted">{term}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  </>
);

const PaymentHistory = ({ sortPaymentHistory, paymentDocuments, currency }) => {
  const { containerRef, card, show, hide } = useHoverCard();
  const [requestedPage, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const scrollRef = useRef(null);
  const rows = sortPaymentHistory;
  const lastPage =
    rowsPerPage === -1
      ? 0
      : Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1);
  const page = Math.min(requestedPage, lastPage);
  const start = rowsPerPage === -1 ? 0 : page * rowsPerPage;
  const visibleRows =
    rowsPerPage === -1 ? rows : rows.slice(start, start + rowsPerPage);
  // On desktop every row is a fifth of the table's height, so five rows fill
  // it at any page size: longer pages scroll inside the panel without
  // resizing the rows, and a short last page keeps the same row height.
  const rowHeight =
    "h-[46px] min-[1360px]:[height:max(40px,calc((100cqh_-_34px)_/_5))]";

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, [page, rowsPerPage]);

  return (
    <section
      className={historyPanel}
      aria-labelledby="payments-title"
      ref={containerRef}
    >
      <header className={historyHeading}>
        <h2 id="payments-title" className={sectionTitle}>
          Payment history
        </h2>
        <p className={panelNote}>Most recent first</p>
      </header>
      {rows.length ? (
        <div
          className="relative min-h-[234px] flex-[1_1_0] overflow-y-auto [contain:size] [container-type:size] [scrollbar-color:#a996e0_#ffffff26] [scrollbar-width:thin] max-[700px]:h-[900px] max-[700px]:min-h-0 max-[700px]:flex-none"
          ref={scrollRef}
          onScroll={hide}
          tabIndex={0}
          role="region"
          aria-label="Payment history rows"
        >
          <table className="w-full table-fixed border-separate border-spacing-0 text-left max-[700px]:block">
            <caption className="sr-only">Recorded payment history</caption>
            {/* Column widths follow the table's width: the design's
                144/164/130px at full width, narrowing in proportion so
                Additional info keeps room. Tables 650px and wider use
                larger text, so Date paid starts at 106px there. */}
            <colgroup className="max-[700px]:hidden">
              <col className="w-1/4" />
              <col className="w-[clamp(96px,13.66cqi,144px)] max-[1359px]:w-[110px] min-[1360px]:[@container_(min-width:650px)]:w-[clamp(106px,13.66cqi,144px)]" />
              <col className="w-[clamp(110px,15.56cqi,164px)] max-[1359px]:w-[110px]" />
              <col />
              <col className="w-[clamp(90px,12.34cqi,130px)] max-[1359px]:w-[100px]" />
            </colgroup>
            <thead className="sticky top-0 z-[1] max-[700px]:absolute max-[700px]:size-px max-[700px]:overflow-hidden max-[700px]:whitespace-nowrap max-[700px]:[clip-path:inset(50%)]">
              <tr>
                {[
                  "Paid by",
                  "Date paid",
                  "Payment method",
                  "Additional info",
                  "Amount paid",
                ].map((label) => (
                  <th
                    scope="col"
                    key={label}
                    className={`${cell} h-7 border-b border-[#23273517] bg-spot-thead text-left text-[12px] text-spot-muted`}
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="before:table-row before:h-1.5 before:content-[''] max-[700px]:block max-[700px]:before:hidden">
              {visibleRows.map((payment, index) => {
                const document = paymentDocuments?.get(payment.id);
                const rowCell = `${cell} text-[13px] [@container_(min-width:650px)]:text-[14.5px] ${index > 0 ? "border-t border-[#23273512]" : ""} max-[700px]:min-w-0 max-[700px]:border-0 max-[700px]:p-0 max-[700px]:text-left`;
                return (
                  <tr
                    key={payment.id}
                    className={`${rowHeight} [&:hover>*]:bg-[#5b47a00a] max-[700px]:grid max-[700px]:h-auto max-[700px]:min-h-[180px] max-[700px]:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)] max-[700px]:content-center max-[700px]:gap-x-3 max-[700px]:gap-y-4 max-[700px]:py-[18px] ${index > 0 ? "max-[700px]:border-t max-[700px]:border-[#23273512]" : ""}`}
                    onMouseMove={
                      document
                        ? (event) =>
                            show(
                              event,
                              <PaymentDocument
                                document={document}
                                currency={currency}
                              />,
                            )
                        : undefined
                    }
                    onMouseLeave={hide}
                  >
                    <th
                      scope="row"
                      className={`${rowCell} text-spot-ink max-[700px]:col-start-1 max-[700px]:row-start-1`}
                    >
                      <span className="flex items-center gap-3 text-[14.5px] font-medium [@container_(min-width:650px)]:text-[16px]">
                        <span
                          className={`grid size-8 shrink-0 place-items-center rounded-[11px] text-[11px] font-normal ${AVATAR_TONES[(start + index) % 4]}`}
                          aria-hidden="true"
                        >
                          {(payment.paidBy || "?")
                            .split(/\s+/)
                            .filter((word) => /[\p{L}\p{N}]/u.test(word))
                            .slice(0, 2)
                            .map((word) => word[0])
                            .join("")
                            .toUpperCase()}
                        </span>
                        <span className="min-w-0">
                          {payment.paidBy || "—"}
                          {document && (
                            <span className="sr-only">
                              , for {document.documentType}{" "}
                              {document.documentNumber}
                            </span>
                          )}
                        </span>
                      </span>
                    </th>
                    <td
                      className={`${rowCell} text-spot-body max-[700px]:col-start-1 max-[700px]:row-start-2`}
                    >
                      <span className={mobileField} aria-hidden="true">
                        Date paid
                      </span>
                      {formatDate(payment.datePaid)}
                    </td>
                    <td
                      className={`${rowCell} text-spot-body max-[700px]:col-start-2 max-[700px]:row-start-2`}
                    >
                      <span className={mobileField} aria-hidden="true">
                        Payment method
                      </span>
                      {payment.paymentMethod || "—"}
                    </td>
                    <td
                      className={`${rowCell} whitespace-pre-wrap text-spot-muted max-[700px]:col-span-full max-[700px]:row-start-3`}
                    >
                      <span className={mobileField} aria-hidden="true">
                        Additional info
                      </span>
                      {payment.additionalInfo || "—"}
                    </td>
                    <td
                      className={`${rowCell} max-[700px]:col-start-2 max-[700px]:row-start-1`}
                    >
                      <span className={mobileField} aria-hidden="true">
                        Amount paid
                      </span>
                      <PaymentAmount
                        amount={payment.amountPaid}
                        currency={currency}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="flex min-h-[248px] flex-1 flex-col items-center justify-center gap-2 text-center text-spot-muted max-[700px]:min-h-[280px] [&>svg]:mb-2 [&>svg]:size-8 [&>svg]:text-spot-violet">
          <FiArrowDownLeft aria-hidden="true" />
          <h3 className="font-display text-[18px] font-light text-spot-ink">
            No payments as of now.
          </h3>
          <p className="text-[13px]">
            Your recorded payments will appear here.
          </p>
        </div>
      )}
      <div className="flex flex-[0_0_36px] items-center justify-between gap-3 border-t border-[#23273512] pt-1 max-[700px]:flex-wrap max-[700px]:pt-3 coarse:basis-[50px]">
        <label className="flex items-center gap-2 text-[12.5px] text-spot-muted">
          Rows per page
          <select
            className={pageSizeSelect}
            value={rowsPerPage}
            onChange={(event) => {
              setRowsPerPage(Number(event.target.value));
              setPage(0);
            }}
          >
            {[5, 10, 25, -1].map((count) => (
              <option key={count} value={count}>
                {count === -1 ? "All" : count}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-center gap-1.5 max-[700px]:ml-auto max-[700px]:flex-wrap">
          <span
            className="mr-2 text-[12.5px] text-spot-text max-[700px]:mr-0.5"
            role="status"
          >
            {rows.length ? start + 1 : 0}–{start + visibleRows.length} of{" "}
            {rows.length}
          </span>
          {[
            {
              label: "First page",
              icon: FiChevronsLeft,
              target: 0,
              disabled: page === 0,
            },
            {
              label: "Previous page",
              icon: FiChevronLeft,
              target: page - 1,
              disabled: page === 0,
            },
            {
              label: "Next page",
              icon: FiChevronRight,
              target: page + 1,
              disabled: page === lastPage,
              primary: true,
            },
            {
              label: "Last page",
              icon: FiChevronsRight,
              target: lastPage,
              disabled: page === lastPage,
            },
          ].map(({ label, icon: Icon, target, disabled, primary }) => (
            <button
              type="button"
              key={label}
              className={`${pagerButton} ${primary ? "enabled:bg-white enabled:shadow-key" : ""}`}
              aria-label={label}
              disabled={disabled}
              onClick={() => setPage(target)}
            >
              <Icon aria-hidden="true" />
            </button>
          ))}
        </div>
      </div>
      <HoverCard card={card} />
    </section>
  );
};

export default PaymentHistory;
