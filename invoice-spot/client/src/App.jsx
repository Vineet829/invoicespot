import { useSelector } from "react-redux";
import { Route, Routes } from "react-router-dom";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.min.css";
import NotFound from "./components/NotFound";
import ScrollToTop from "./components/ScrollToTop";
import HomePage from "./pages/HomePage";
import RegisterPage from "./features/auth/pages/RegisterPage";
import VerifiedPage from "./features/auth/pages/VerifiedPage";
import OAuthCallbackPage from "./features/auth/pages/OAuthCallbackPage";
import LoginPage from "./features/auth/pages/LoginPage";
import ResendEmailTokenPage from "./features/auth/pages/ResendEmailTokenPage";
import PasswordResetRequestPage from "./features/auth/pages/PasswordResetRequestPage";
import PasswordResetPage from "./features/auth/pages/PasswordResetPage";
import { ROLES } from "./config/roles";
import UsersList from "./features/users/pages/UsersListPage";
import AuditLogPage from "./features/users/pages/AuditLogPage";
import DashboardPage from "./features/dashboard/pages/DashboardPage";
import AuthRequired from "./components/AuthRequired";
import EditProfileForm from "./features/users/pages/EditProfileForm";
import ProfilePage from "./features/users/pages/ProfilePage";
import CustomerCreateForm from "./features/customers/pages/CustomerCreateForm";
import CustomerEditForm from "./features/customers/pages/CustomerEditForm";
import CustomersPage from "./features/customers/pages/CustomersPage";
import SingleCustomerPage from "./features/customers/pages/SingleCustomerPage";
import DocCreateEditForm from "./features/documents/pages/DocCreateEditForm";
import DocumentsPage from "./features/documents/pages/DocumentsPage";
import PaymentsPage from "./features/payments/pages/PaymentsPage";

import SingleDocumentPage from "./features/documents/pages/SingleDocumentPage";
import SettingsPage from "./features/settings/pages/SettingsPage";
import SecurityPage from "./features/settings/pages/SecurityPage";
import BusinessProfilePage from "./features/settings/pages/BusinessProfilePage";
import BusinessGate from "./features/business/BusinessGate";
import WelcomePage from "./features/business/pages/WelcomePage";
import AcceptInvitePage from "./features/business/pages/AcceptInvitePage";
import TeamPage from "./features/settings/pages/TeamPage";
import PaymentDetailsPage from "./features/settings/pages/PaymentDetailsPage";
import EmailTemplatesPage from "./features/settings/pages/EmailTemplatesPage";
import ItemsPage from "./features/items/pages/ItemsPage";
import ExpensesPage from "./features/expenses/pages/ExpensesPage";
import SharedDocumentPage from "./features/public/pages/SharedDocumentPage";
import ClientPortalPage from "./features/portal/pages/ClientPortalPage";
import PortalDocumentPage from "./features/portal/pages/PortalDocumentPage";
import RecurringPage from "./features/recurring/pages/RecurringPage";
import ReportsPage from "./features/reports/pages/ReportsPage";

const App = () => {
  // Nothing draws while a reload gets its access token back, so no page sees
  // someone signed in without one.
  const restoring = useSelector((state) => state.auth.restoring);
  if (restoring) return null;
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/">
          <Route index element={<HomePage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="auth/verify" element={<VerifiedPage />} />
          <Route path="auth/callback" element={<OAuthCallbackPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="resend" element={<ResendEmailTokenPage />} />
          <Route
            path="reset_password_request"
            element={<PasswordResetRequestPage />}
          />
          <Route path="auth/reset_password" element={<PasswordResetPage />} />
          <Route path="p/:token" element={<SharedDocumentPage />} />
          <Route path="c/:token" element={<ClientPortalPage />} />
          <Route path="c/:token/doc" element={<PortalDocumentPage />} />
          <Route path="invite/:token" element={<AcceptInvitePage />} />
          {/* Private Routes - Users */}
          <Route element={<AuthRequired allowedRoles={[ROLES.User]} />}>
            <Route path="profile" element={<ProfilePage />} />
            <Route path="edit-profile" element={<EditProfileForm />} />
            <Route path="settings/security" element={<SecurityPage />} />
            <Route path="welcome" element={<WelcomePage />} />
            {/* Pages that work in the session's business */}
            <Route element={<BusinessGate />}>
              <Route path="settings" element={<SettingsPage />} />
              <Route
                path="settings/business"
                element={<BusinessProfilePage />}
              />
              <Route
                path="settings/payments"
                element={<PaymentDetailsPage />}
              />
              <Route path="settings/emails" element={<EmailTemplatesPage />} />
              <Route path="settings/team" element={<TeamPage />} />
              <Route path="items" element={<ItemsPage />} />
              <Route path="expenses" element={<ExpensesPage />} />
              <Route path="customers" element={<CustomersPage />} />
              <Route path="create-customer" element={<CustomerCreateForm />} />
              <Route
                path="single-customer/:custId"
                element={<SingleCustomerPage />}
              />
              <Route
                path="edit-customer/:custId"
                element={<CustomerEditForm />}
              />
              <Route path="documents" element={<DocumentsPage />} />
              <Route path="payments" element={<PaymentsPage />} />
              <Route path="recurring" element={<RecurringPage />} />
              <Route path="create-doc" element={<DocCreateEditForm />} />
              <Route path="edit-doc/:id" element={<DocCreateEditForm />} />
              <Route path="document/:id" element={<SingleDocumentPage />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="reports" element={<ReportsPage />} />
            </Route>
          </Route>

          <Route element={<AuthRequired allowedRoles={[ROLES.Admin]} />}>
            <Route path="users" element={<UsersList />} />
            <Route path="users/audit" element={<AuditLogPage />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
      <ToastContainer
        theme="light"
        toastClassName="rounded-2xl border border-solid border-white font-ui text-[14px] text-spot-ink shadow-float"
      />
    </>
  );
};

export default App;
