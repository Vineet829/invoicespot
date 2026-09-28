import { useState } from "react";
import {
  FiArrowDownLeft,
  FiChevronLeft,
  FiChevronRight,
  FiChevronsLeft,
  FiChevronsRight,
  FiCreditCard,
  FiDollarSign,
  FiGlobe,
  FiMoreHorizontal,
  FiRepeat,
  FiSmartphone,
} from "react-icons/fi";
import { formatDate, formatMoney } from "../../dashboardModel";

const payerTone = (name) =>
  Array.from(name || "").reduce(
    (sum, character) => sum + character.codePointAt(0),
    0,
  ) % 3;

const MethodLabel = ({ method }) => {
  const Icon =
    {
      "Bank Transfer": FiRepeat,
      "Credit Card": FiCreditCard,
      Cash: FiDollarSign,
      "Mobile Money": FiSmartphone,
      PayPal: FiGlobe,
    }[method] || FiMoreHorizontal;
  return (
    <span className="dashboard-method-label">
      <Icon aria-hidden="true" />
      {method || "—"}
    </span>
  );
};

const PaymentAmount = ({ amount }) => {
  const formatted = formatMoney(amount);
  return (
    <span className="dashboard-payment-value">
      {formatted.slice(0, -3)}
      <span className="dashboard-payment-decimal">{formatted.slice(-3)}</span>
    </span>
  );
};

const PaymentHistory = ({ sortPaymentHistory }) => {
  const [requestedPage, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const rows = sortPaymentHistory;
  const lastPage =
    rowsPerPage === -1
      ? 0
      : Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1);
  const page = Math.min(requestedPage, lastPage);
  const start = rowsPerPage === -1 ? 0 : page * rowsPerPage;
  const visibleRows =
    rowsPerPage === -1 ? rows : rows.slice(start, start + rowsPerPage);

  return (
    <section
      className="dashboard-panel dashboard-history"
      aria-labelledby="payments-title"
    >
      <div className="dashboard-panel-header">
        <div className="dashboard-history-heading">
          <span className="dashboard-history-heading-icon" aria-hidden="true">
            <FiArrowDownLeft />
          </span>
          <div>
            <h2 id="payments-title">Payment history</h2>
            <p>Your recorded payments, most recent first.</p>
          </div>
        </div>
        <span className="dashboard-count-badge">
          {rows.length} {rows.length === 1 ? "payment" : "payments"}
        </span>
      </div>
      <div className="dashboard-history-body">
        {rows.length ? (
          <table className="dashboard-history-table">
            <caption className="dashboard-sr-only">
              Recorded payment history
            </caption>
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Paid by</th>
                <th scope="col">Date paid</th>
                <th scope="col">Amount paid</th>
                <th scope="col">Payment method</th>
                <th scope="col">Additional info</th>
              </tr>
            </thead>
            <tbody>
              {visibleRows.map((payment, index) => (
                <tr
                  key={payment.id}
                  style={{
                    "--payment-row-delay": `${Math.min(index, 4) * 35}ms`,
                  }}
                >
                  <td className="dashboard-history-index">
                    <span className="dashboard-mobile-field" aria-hidden="true">
                      Payment
                    </span>
                    {start + index + 1}
                  </td>
                  <th scope="row" className="dashboard-history-name">
                    <span className="dashboard-payer">
                      <span
                        className="dashboard-payer-avatar"
                        aria-hidden="true"
                        data-tone={payerTone(payment.paidBy)}
                      >
                        {(payment.paidBy || "?")
                          .split(/\s+/)
                          .filter((word) => /[\p{L}\p{N}]/u.test(word))
                          .slice(0, 2)
                          .map((word) => word[0])
                          .join("")
                          .toUpperCase()}
                      </span>
                      <span>{payment.paidBy || "—"}</span>
                    </span>
                  </th>
                  <td className="dashboard-history-date">
                    <span className="dashboard-mobile-field" aria-hidden="true">
                      Date paid
                    </span>
                    {formatDate(payment.datePaid)}
                  </td>
                  <td className="dashboard-history-amount">
                    <span className="dashboard-mobile-field" aria-hidden="true">
                      Amount paid
                    </span>
                    <PaymentAmount amount={payment.amountPaid} />
                  </td>
                  <td className="dashboard-history-method">
                    <span className="dashboard-mobile-field" aria-hidden="true">
                      Payment method
                    </span>
                    <MethodLabel method={payment.paymentMethod} />
                  </td>
                  <td className="dashboard-history-note">
                    <span className="dashboard-mobile-field" aria-hidden="true">
                      Additional info
                    </span>
                    {payment.additionalInfo || "No additional info"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="dashboard-empty-state">
            <span className="dashboard-empty-icon">
              <FiArrowDownLeft aria-hidden="true" />
            </span>
            <h3>No payments as of now.</h3>
            <p>Your recorded payments will appear here.</p>
          </div>
        )}
        <div className="dashboard-pagination">
          <label className="dashboard-page-size">
            Rows per page
            <select
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
          <span role="status">
            {rows.length ? start + 1 : 0}–{start + visibleRows.length} of{" "}
            {rows.length}
          </span>
          <div className="dashboard-page-actions">
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
              },
              {
                label: "Last page",
                icon: FiChevronsRight,
                target: lastPage,
                disabled: page === lastPage,
              },
            ].map(({ label, icon: Icon, target, disabled }) => (
              <button
                key={label}
                className="dashboard-icon-button"
                aria-label={label}
                disabled={disabled}
                onClick={() => setPage(target)}
              >
                <Icon aria-hidden="true" />
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};

export default PaymentHistory;
