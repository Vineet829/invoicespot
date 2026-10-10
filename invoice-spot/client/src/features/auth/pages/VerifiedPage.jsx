import { FiCheckCircle, FiLogIn } from "react-icons/fi";
import { Link } from "react-router-dom";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import { primaryButton } from "../../../components/workspace/pastel";

// The backend verifies the emailed link, then sends the visitor here.
const VerifiedPage = () => {
  useTitle("Verify User - Invoice Spot");
  return (
    <PublicPage>
      <CardHeading icon={FiCheckCircle} title="Account verified">
        Your account has been verified and is ready for use. An email confirming
        this has been sent.
      </CardHeading>
      <Link to="/login" className={`${primaryButton} mt-7 w-full`}>
        <FiLogIn aria-hidden="true" />
        Log in to continue
      </Link>
    </PublicPage>
  );
};

export default VerifiedPage;
