import { useMemo } from "react";
import {
  FiAlertCircle,
  FiArrowDownLeft,
  FiClock,
  FiTrendingUp,
} from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetAllUserCustomersQuery } from "../../customers/customersApiSlice";
import { useGetDashboardDocumentsQuery } from "../dashboardApiSlice";
import {
  formatMoney,
  getDashboardSnapshot,
  getFinanceBarRatios,
  getPaymentDocuments,
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
  pageSubtitle,
  pageTitle,
  panelNote,
  retryButton,
  sectionTitle,
} from "../../../components/workspace/pastel";
import PaymentHistory from "./components/paymentHistory";

const EMPTY_DOCUMENTS = [];

const DashboardPage = () => {
  useTitle("Business overview — Invoice Spot");
  const documentsQuery = useGetDashboardDocumentsQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const customersQuery = useGetAllUserCustomersQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const documents = documentsQuery.data?.myDocuments || EMPTY_DOCUMENTS;
  const snapshot = useMemo(() => getDashboardSnapshot(documents), [documents]);
  const paymentDocuments = useMemo(
    () => getPaymentDocuments(documents),
    [documents],
  );
  const documentCount = documentsQuery.data?.totalDocuments;
  const refreshing = documentsQuery.isFetching || customersQuery.isFetching;
  const hasError = documentsQuery.isError || customersQuery.isError;
  const bars = getFinanceBarRatios(snapshot);
  const today = new Date().toLocaleDateString("en-US", {
    weekday: "long",
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const refresh = () => {
    documentsQuery.refetch();
    customersQuery.refetch();
  };

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
            customerCount={customersQuery.data?.totalCustomers}
            documentCount={documentCount}
            customersLoading={customersQuery.isLoading}
            documentsLoading={documentsQuery.isLoading}
            customersError={customersQuery.isError}
          />
        </header>
        {hasError && (
          <div className={errorBanner} role="alert">
            <FiAlertCircle aria-hidden="true" />
            <div className="flex-1">
              <strong className={errorTitle}>
                {documentsQuery.isError && !documentsQuery.data
                  ? "We couldn’t load your overview."
                  : "Some workspace data couldn’t be refreshed."}
              </strong>
              <span className={errorDetail}>
                {documentsQuery.data
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
        {documentsQuery.isLoading ? (
          <DashboardLoading />
        ) : (
          documentsQuery.data && (
            <div className={overviewLayout}>
              <div className={mainColumn}>
                <section
                  className={financePanel}
                  aria-labelledby="financial-summary-title"
                >
                  <header className={financePanelHeading}>
                    <h2 id="financial-summary-title" className={sectionTitle}>
                      Financial summary
                    </h2>
                    {bars && (
                      <p className={panelNote}>
                        Bars compare each amount with expected income
                      </p>
                    )}
                  </header>
                  <div className={financeCards}>
                    <FinancialCard
                      label="Expected income"
                      value={formatMoney(snapshot.expectedIncome)}
                      detail="Total document value"
                      icon={FiTrendingUp}
                      tone="blue"
                      fill={bars?.expected}
                    />
                    <FinancialCard
                      label="Cash received"
                      value={formatMoney(snapshot.cashReceived)}
                      detail="Total amount received"
                      icon={FiArrowDownLeft}
                      tone="lilac"
                      fill={bars?.received}
                    />
                    <FinancialCard
                      label="Cash pending"
                      value={formatMoney(snapshot.cashPending)}
                      detail="Expected income less cash received"
                      icon={FiClock}
                      tone="peach"
                      fill={bars?.pending}
                    />
                  </div>
                </section>
                <PaymentHistory
                  sortPaymentHistory={snapshot.payments}
                  paymentDocuments={paymentDocuments}
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
