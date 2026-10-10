import { useState } from "react";
import { Link } from "react-router-dom";
import { FiBriefcase } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import { Field, errorMessage } from "../../../components/workspace/Workspace";
import { primaryButton, textLink } from "../../../components/workspace/pastel";
import { useCreateBusinessMutation } from "../businessApiSlice";
import useEnterBusiness from "../useEnterBusiness";

// Where someone who works in no business lands: they start one here, or wait
// to be invited to someone else's.
const WelcomePage = () => {
  useTitle("Start a business — Invoice Spot");
  const [name, setName] = useState("");
  const [create, { isLoading }] = useCreateBusinessMutation();
  const enter = useEnterBusiness();
  const submit = async (event) => {
    event.preventDefault();
    try {
      const business = await create({ name }).unwrap();
      toast.success(`${business.name} is ready.`);
      enter(business._id);
    } catch (error) {
      toast.error(errorMessage(error, "The business could not be started."));
    }
  };
  return (
    <PublicPage>
      <CardHeading icon={FiBriefcase} title="Start a business">
        You aren’t part of a business yet. Start one to send quotations and
        invoices, or ask someone to invite you to theirs.
      </CardHeading>
      <form className="mt-6 flex flex-col gap-4" onSubmit={submit}>
        <Field
          label="Business name"
          name="name"
          required
          maxLength={255}
          autoComplete="organization"
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={isLoading}
          data-autofocus
        />
        <button type="submit" className={primaryButton} disabled={isLoading}>
          {isLoading ? "Starting…" : "Start business"}
        </button>
      </form>
      <p className="mt-5 text-[13.5px] text-spot-muted">
        Or go to{" "}
        <Link className={textLink} to="/profile">
          your profile
        </Link>
        .
      </p>
    </PublicPage>
  );
};

export default WelcomePage;
