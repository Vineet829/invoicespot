import { FiAlertCircle, FiCheckCircle, FiLogIn, FiMail } from "react-icons/fi";
import { Link, useSearchParams } from "react-router-dom";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import { primaryButton } from "../../../components/workspace/pastel";

// The backend checks the emailed link, then sends the visitor here: verified,
// verified before, or with a link that expired or was already used.
const VerifiedPage = () => {
  useTitle("Verify User - Invoice Spot");
  const [params] = useSearchParams();
  if (params.has("expired")) {
    return (
      <PublicPage>
        <CardHeading icon={FiAlertCircle} title="This link has expired">
          Verification links last 24 hours and work once. Ask for a new one and
          use the link in that email.
        </CardHeading>
        <Link to="/resend" className={`${primaryButton} mt-7 w-full`}>
          <FiMail aria-hidden="true" />
          Send a new link
        </Link>
      </PublicPage>
    );
  }
  return (
    <PublicPage>
      <CardHeading
        icon={FiCheckCircle}
        title={
          params.has("already")
            ? "Account already verified"
            : "Account verified"
        }
      >
        {params.has("already")
          ? "Your account was verified before, so you can log in."
          : "Your account has been verified and is ready for use. An email confirming this has been sent."}
      </CardHeading>
      <Link to="/login" className={`${primaryButton} mt-7 w-full`}>
        <FiLogIn aria-hidden="true" />
        Log in to continue
      </Link>
    </PublicPage>
  );
};

export default VerifiedPage;
