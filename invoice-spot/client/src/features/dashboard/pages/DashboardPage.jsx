import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import {
  FiAlertCircle,
  FiArrowDownLeft,
  FiClock,
  FiTrendingUp,
} from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetOverviewQuery } from "../dashboardApiSlice";
import {
  formatMoney,
  getFinanceBarRatios,
  paymentRows,
} from "../dashboardModel";
import DashboardShell from "../components/DashboardShell";
import {
  DashboardLoading,
  DocumentStatus,
  FinancialCard,
  OverviewTotals,
  financeCards,
  financePanel,
  financePanelHeading,
  mainColumn,
  overviewLayout,
} from "../components/DashboardPanels";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  pageSizeSelect,
  pageSubtitle,
  pageTitle,
  panelNote,
  retryButton,
  sectionTitle,
} from "../../../components/workspace/pastel";
import PaymentHistory from "./components/paymentHistory";

const NO_PAYMENTS = [];

const DashboardPage = () => {
  useTitle("Business overview — Invoice Spot");
  const [params, setParams] = useSearchParams();
  const overviewQuery = useGetOverviewQuery(
    params.get("currency") || undefined,
    { refetchOnMountOrArgChange: 60 },
  );
  const snapshot = overviewQuery.data;
  const payments = useMemo(
    () => paymentRows(snapshot?.payments || NO_PAYMENTS),
    [snapshot],
  );
  const documentCount = snapshot?.documents;
  const refreshing = overviewQuery.isFetching;
  const hasError = overviewQuery.isError;
  const bars = snapshot && getFinanceBarRatios(snapshot);
  // Money is shown in one currency at a time; the switch appears only when
  // there is more than one, and the default stays out of the address.
  const chooseCurrency = (currency) =>
    setParams(currency === snapshot.currencies[0] ? {} : { currency }, {
      replace: true,
    });
  const today = new Date().toLocaleDateString("en-US", {
    weekday: "long",
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const refresh = () => overviewQuery.refetch();

  return (
    <DashboardShell fit>
      <div
        className={`flex min-h-0 min-w-0 flex-1 flex-col gap-[22px] compact:gap-4${hasError ? " has-error" : ""}`}
      >
        <header className="flex flex-[0_0_84px] animate-ov-rise items-end justify-between gap-8 compact:basis-[72px] max-[1359px]:items-start max-[1359px]:gap-5 max-[700px]:flex-wrap">
          <div className="min-w-0">
            <h1 className={pageTitle}>
              Business overview<span>.</span>
            </h1>
            <p className={pageSubtitle}>
              Your documents, payments, and customers at a glance{" "}
              <span className="whitespace-nowrap max-[700px]:whitespace-normal">
                · {today}
              </span>
            </p>
          </div>
          <OverviewTotals
            customerCount={snapshot?.clients}
            documentCount={documentCount}
            customersLoading={overviewQuery.isLoading}
            documentsLoading={overviewQuery.isLoading}
            customersError={hasError && !snapshot}
          />
        </header>
        {hasError && (
          <div className={errorBanner} role="alert">
            <FiAlertCircle aria-hidden="true" />
            <div className="flex-1">
              <strong className={errorTitle}>
                {!snapshot
                  ? "We couldn’t load your overview."
                  : "Some workspace data couldn’t be refreshed."}
              </strong>
              <span className={errorDetail}>
                {snapshot
                  ? "Your last loaded figures are still shown. Try refreshing."
                  : "Please try again to see your latest figures."}
              </span>
            </div>
            <button
              type="button"
              className={retryButton}
              onClick={refresh}
              disabled={refreshing}
            >
              Try again
            </button>
          </div>
        )}
        {overviewQuery.isLoading ? (
          <DashboardLoading />
        ) : (
          snapshot && (
            <div className={overviewLayout}>
              <div className={mainColumn}>
                <section
                  className={financePanel}
                  aria-labelledby="financial-summary-title"
                >
                  <header className={financePanelHeading}>
                    <div className="flex min-w-0 items-center gap-3">
                      <h2 id="financial-summary-title" className={sectionTitle}>
                        Financial summary
                      </h2>
                      {snapshot.currencies.length > 1 && (
                        <label className="flex items-center gap-2 text-[12.5px] text-spot-muted">
                          <span className="sr-only">Currency</span>
                          <select
                            className={pageSizeSelect}
                            value={snapshot.currency}
                            onChange={(event) =>
                              chooseCurrency(event.target.value)
                            }
                          >
                            {snapshot.currencies.map((currency) => (
                              <option key={currency} value={currency}>
                                {currency}
                              </option>
                            ))}
                          </select>
                        </label>
                      )}
                    </div>
                    {bars && (
                      <p className={panelNote}>
                        Bars compare each amount with expected income
                      </p>
                    )}
                  </header>
                  <div className={financeCards}>
                    <FinancialCard
                      label="Expected income"
                      value={formatMoney(
                        snapshot.expectedIncome,
                        snapshot.currency,
                      )}
                      detail="Invoices and receipts"
                      icon={FiTrendingUp}
                      tone="blue"
                      fill={bars?.expected}
                    />
                    <FinancialCard
                      label="Cash received"
                      value={formatMoney(
                        snapshot.cashReceived,
                        snapshot.currency,
                      )}
                      detail="Total amount received"
                      icon={FiArrowDownLeft}
                      tone="lilac"
                      fill={bars?.received}
                    />
                    <FinancialCard
                      label="Cash pending"
                      value={formatMoney(
                        snapshot.cashPending,
                        snapshot.currency,
                      )}
                      detail={
                        Number(snapshot.taxWithheld) > 0
                          ? "Expected income less cash received and tax withheld"
                          : "Expected income less cash received"
                      }
                      icon={FiClock}
                      tone="peach"
                      fill={bars?.pending}
                    />
                  </div>
                </section>
                <PaymentHistory
                  sortPaymentHistory={payments.rows}
                  paymentDocuments={payments.documents}
                  currency={snapshot.currency}
                />
              </div>
              <DocumentStatus snapshot={snapshot} total={documentCount} />
            </div>
          )
        )}
      </div>
    </DashboardShell>
  );
};

export default DashboardPage;
