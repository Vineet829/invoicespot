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
  panelNote,
  sectionTitle,
} from "../components/DashboardPanels";
import PaymentHistory from "./components/paymentHistory";
import "../../../styles/dashboard.css";

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
    <DashboardShell appearance="pastel">
      <div
        className={`flex min-h-0 min-w-0 flex-1 flex-col gap-[22px] compact:gap-4${hasError ? " has-error" : ""}`}
      >
        <header className="flex flex-[0_0_84px] animate-ov-rise items-end justify-between gap-8 compact:basis-[72px] max-[1359px]:items-start max-[1359px]:gap-5 max-[700px]:flex-wrap">
          <div className="min-w-0">
            <h1 className="font-display text-[54px] font-extralight leading-none tracking-[-0.055em] compact:text-[46px] max-[1359px]:text-[38px] max-[700px]:text-[clamp(29px,7.4vw,38px)] [&>span]:text-spot-brand">
              Business overview<span>.</span>
            </h1>
            <p className="mt-2.5 text-[14.5px] text-spot-sub compact:mt-2 max-[1359px]:text-[13px] max-[700px]:leading-[1.6]">
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
          <div
            className="m-0 flex items-center gap-3.5 rounded-xl border border-white/90 bg-overdue/85 px-5 py-[18px] text-overdue-ink max-[600px]:flex-wrap max-[600px]:items-start [&>svg]:size-5 [&>svg]:shrink-0"
            role="alert"
          >
            <FiAlertCircle aria-hidden="true" />
            <div className="flex-1">
              <strong className="block text-[14px] font-medium">
                {documentsQuery.isError && !documentsQuery.data
                  ? "We couldn’t load your overview."
                  : "Some workspace data couldn’t be refreshed."}
              </strong>
              <span className="mt-1 block text-[12px] text-overdue-muted">
                {documentsQuery.data
                  ? "Your last loaded figures are still shown. Try refreshing."
                  : "Please try again to see your latest figures."}
              </span>
            </div>
            <button
              type="button"
              className="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-lg border border-spot-violet/15 bg-white/70 px-4 py-[11px] text-[13px] font-medium leading-[1.4] text-spot-night transition-colors hover:enabled:bg-white max-[600px]:ml-[34px]"
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
