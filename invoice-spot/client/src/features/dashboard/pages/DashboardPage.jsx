import { useMemo } from "react";
import {
  FiAlertCircle,
  FiArrowDownLeft,
  FiCheckCircle,
  FiClock,
  FiFileText,
  FiMinusCircle,
  FiPieChart,
  FiTrendingUp,
  FiUsers,
} from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetAllUserCustomersQuery } from "../../customers/customersApiSlice";
import { useGetDashboardDocumentsQuery } from "../dashboardApiSlice";
import { formatMoney, getDashboardSnapshot } from "../dashboardModel";
import DashboardShell from "../components/DashboardShell";
import {
  DashboardLoading,
  StatCard,
  StatusRings,
} from "../components/DashboardPanels";
import PaymentHistory from "./components/paymentHistory";
import "../../../styles/dashboard.css";
import "../../../styles/dashboard-pastel.css";

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
  const customerCount = customersQuery.data?.totalCustomers;
  const documentCount = documentsQuery.data?.totalDocuments;
  const refreshing = documentsQuery.isFetching || customersQuery.isFetching;
  const hasError = documentsQuery.isError || customersQuery.isError;
  const showBalanceGauges =
    snapshot.expectedIncome > 0 &&
    snapshot.cashReceived >= 0 &&
    snapshot.cashPending >= 0;

  const refresh = () => {
    documentsQuery.refetch();
    customersQuery.refetch();
  };

  return (
    <DashboardShell
      appearance="pastel"
      documentCount={documentCount}
      customerCount={customerCount}
    >
      <div className="dashboard-overview">
        <header className="dashboard-page-heading">
          <div>
            <h1>
              Business overview<span>.</span>
            </h1>
          </div>
          <p className="dashboard-page-description">
            Your documents, payments, and customers at a glance.
          </p>
        </header>

        {hasError && (
          <div className="dashboard-error" role="alert">
            <FiAlertCircle aria-hidden="true" />
            <div>
              <strong>
                {documentsQuery.isError && !documentsQuery.data
                  ? "We couldn’t load your overview."
                  : "Some workspace data couldn’t be refreshed."}
              </strong>
              <span>
                {documentsQuery.data
                  ? "Your last loaded figures are still shown. Try refreshing."
                  : "Please try again to see your latest figures."}
              </span>
            </div>
            <button
              className="dashboard-button dashboard-button-secondary"
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
            <div className="dashboard-overview-layout">
              <section
                className="dashboard-financial-stats"
                aria-labelledby="financial-summary-title"
              >
                <header className="dashboard-summary-heading">
                  <h2 id="financial-summary-title">Financial summary</h2>
                  <p className="dashboard-section-label">THE BIG PICTURE</p>
                </header>
                <div className="dashboard-financial-cards">
                  <StatCard
                    label="Expected income"
                    value={formatMoney(snapshot.expectedIncome)}
                    detail="Total document value"
                    icon={FiTrendingUp}
                    fill={showBalanceGauges ? 1 : undefined}
                    featured
                  />
                  <StatCard
                    label="Cash received"
                    value={formatMoney(snapshot.cashReceived)}
                    detail="Total amount received"
                    icon={FiArrowDownLeft}
                    fill={
                      showBalanceGauges
                        ? snapshot.cashReceived / snapshot.expectedIncome
                        : undefined
                    }
                    accent
                  />
                  <StatCard
                    label="Cash pending"
                    value={formatMoney(snapshot.cashPending)}
                    detail="Expected income less cash received"
                    icon={FiClock}
                    fill={
                      showBalanceGauges
                        ? snapshot.cashPending / snapshot.expectedIncome
                        : undefined
                    }
                  />
                </div>
              </section>
              <section
                className="dashboard-workspace-summary"
                aria-labelledby="workspace-summary-title"
              >
                <header className="dashboard-summary-heading">
                  <h2 id="workspace-summary-title">Your workspace</h2>
                  <p className="dashboard-section-label">AT A GLANCE</p>
                </header>
                <div className="dashboard-stats dashboard-count-stats">
                  <div
                    className="dashboard-workspace-totals"
                    role="group"
                    aria-label="Customer and document totals"
                  >
                    <StatCard
                      variant="total"
                      label="Clients"
                      value={
                        customerCount == null
                          ? "—"
                          : customerCount.toLocaleString("en")
                      }
                      detail={
                        customersQuery.isError ? "Count unavailable" : undefined
                      }
                      icon={FiUsers}
                    />
                    <StatCard
                      variant="total"
                      label="Documents"
                      value={documentCount.toLocaleString("en")}
                      icon={FiFileText}
                    />
                  </div>
                </div>
              </section>
              <section
                className="dashboard-document-status"
                aria-labelledby="document-status-title"
              >
                <header className="dashboard-summary-heading">
                  <h2 id="document-status-title">Document status</h2>
                  <FiPieChart aria-hidden="true" />
                </header>
                <div className="dashboard-status-body">
                  <StatusRings
                    paid={snapshot.paid}
                    partial={snapshot.partial}
                    unpaid={snapshot.unpaid}
                    total={documentCount}
                  />
                  <div
                    className="dashboard-workspace-statuses"
                    role="group"
                    aria-label="Document status"
                  >
                    <StatCard
                      variant="status"
                      label="Paid"
                      value={snapshot.paid.toLocaleString("en")}
                      icon={FiCheckCircle}
                      tone="paid"
                    />
                    <StatCard
                      variant="status"
                      label="Partially paid"
                      value={snapshot.partial.toLocaleString("en")}
                      icon={FiPieChart}
                      tone="partial"
                    />
                    <StatCard
                      variant="status"
                      label="Unpaid"
                      value={snapshot.unpaid.toLocaleString("en")}
                      icon={FiMinusCircle}
                    />
                  </div>
                </div>
                <div className="dashboard-overdue">
                  <StatCard
                    variant="status"
                    label="Overdue"
                    value={snapshot.overdue.toLocaleString("en")}
                    icon={FiAlertCircle}
                    tone="overdue"
                  />
                  <span className="dashboard-overdue-caption">
                    Past the due date
                  </span>
                </div>
              </section>
              <PaymentHistory sortPaymentHistory={snapshot.payments} />
            </div>
          )
        )}
      </div>
    </DashboardShell>
  );
};

export default DashboardPage;
