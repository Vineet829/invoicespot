import { useEffect } from "react";
import { FiLink, FiLogIn, FiUserPlus, FiUsers } from "react-icons/fi";
import { useSelector } from "react-redux";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import {
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useLogoutUserMutation } from "../../auth/authApiSlice";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import {
  useAcceptInvitationMutation,
  useGetInvitationQuery,
} from "../businessApiSlice";
import { forgetInvite, rememberInvite } from "../pendingInvite";
import { ROLE_SUMMARIES } from "../permissions";
import useEnterBusiness from "../useEnterBusiness";

const AS_A = { ADMIN: "an admin", MEMBER: "a member", VIEWER: "a viewer" };

// Why an invitation can't be accepted any more.
const CLOSED = {
  ACCEPTED: {
    title: "This invitation was already accepted",
    text: "If it was you, the business is in your list of businesses.",
  },
  REVOKED: {
    title: "This invitation was withdrawn",
    text: "Ask whoever invited you to send a new one.",
  },
  EXPIRED: {
    title: "This invitation has expired",
    text: "Invitations are open for 7 days. Ask whoever invited you to send it again.",
  },
};

// Where an emailed invitation link leads. Anyone holding it sees which
// business it is for; only the account it was sent to can accept it.
const AcceptInvitePage = () => {
  useTitle("Invitation — Invoice Spot");
  const { token } = useParams();
  const navigate = useNavigate();
  const signedIn = Boolean(useSelector((state) => state.auth.user));
  const query = useGetInvitationQuery(token);
  const invitation = query.data;
  const profileQuery = useGetUserProfileQuery(undefined, { skip: !signedIn });
  const profile = profileQuery.data?.userProfile;
  const [accept, { isLoading: accepting }] = useAcceptInvitationMutation();
  const [logoutUser, { isLoading: signingOut }] = useLogoutUserMutation();
  const enter = useEnterBusiness();
  const here = { pathname: `/invite/${token}` };
  const closed =
    (query.isError && query.error?.status === 404) ||
    (invitation && invitation.status !== "PENDING");

  // A link that can't be accepted any more shouldn't bring anyone back here
  // after they log in.
  useEffect(() => {
    if (closed) forgetInvite();
  }, [closed]);

  const join = async () => {
    try {
      const business = await accept(token).unwrap();
      forgetInvite();
      toast.success(`You joined ${business.name}.`);
      enter(business._id);
    } catch (error) {
      toast.error(errorMessage(error, "The invitation could not be accepted."));
    }
  };
  const logIn = () => {
    rememberInvite(token);
    navigate("/login", { state: { from: here } });
  };
  const signUp = () => {
    rememberInvite(token);
    navigate("/register", { state: { email: invitation.email } });
  };
  const switchAccount = async () => {
    await logoutUser();
    logIn();
  };

  if (query.isLoading) {
    return (
      <PublicPage>
        <p className={panelNote} role="status">
          Opening the invitation…
        </p>
      </PublicPage>
    );
  }
  if (query.isError) {
    return (
      <PublicPage>
        {query.error?.status === 404 ? (
          <CardHeading icon={FiLink} title="This invitation link doesn’t work">
            A newer invitation may have replaced it. Ask whoever invited you to
            send it again.
          </CardHeading>
        ) : (
          <>
            <CardHeading icon={FiLink} title="We couldn’t open this invitation">
              {errorMessage(query.error)}
            </CardHeading>
            <button
              type="button"
              className={`${secondaryButton} mt-7 w-full`}
              onClick={query.refetch}
            >
              Try again
            </button>
          </>
        )}
      </PublicPage>
    );
  }
  if (invitation.status !== "PENDING") {
    const reason = CLOSED[invitation.status] || CLOSED.REVOKED;
    return (
      <PublicPage>
        <CardHeading icon={FiUsers} title={reason.title}>
          {reason.text}
        </CardHeading>
        <Link
          to={signedIn ? "/dashboard" : "/login"}
          className={`${primaryButton} mt-7 w-full`}
        >
          {signedIn ? "Go to your dashboard" : "Log in"}
        </Link>
      </PublicPage>
    );
  }

  const someoneElse =
    profile && profile.email.toLowerCase() !== invitation.email.toLowerCase();
  return (
    <PublicPage>
      <CardHeading icon={FiUsers} title={`Join ${invitation.businessName}`}>
        {invitation.invitedBy || "Someone"} invited{" "}
        <strong className="font-medium text-spot-ink">
          {invitation.email}
        </strong>{" "}
        to work in {invitation.businessName} as {AS_A[invitation.role]}.
      </CardHeading>
      <p className={`mt-3 ${panelNote}`}>{ROLE_SUMMARIES[invitation.role]}</p>
      <p className={`mt-1.5 ${panelNote}`}>
        Open until {dateLabel(invitation.expiresAt)}.
      </p>
      {!signedIn ? (
        <div className="mt-7 flex flex-col gap-2.5">
          <button type="button" className={primaryButton} onClick={logIn}>
            <FiLogIn aria-hidden="true" /> Log in to accept
          </button>
          <button type="button" className={secondaryButton} onClick={signUp}>
            <FiUserPlus aria-hidden="true" /> Create an account
          </button>
          <p className={`mt-1 ${panelNote}`}>
            New here? Sign up with {invitation.email}, verify it, then log in to
            accept.
          </p>
        </div>
      ) : someoneElse ? (
        <div className="mt-7 flex flex-col gap-2.5">
          <p className="text-[14px] leading-[1.6] text-spot-body" role="note">
            You’re logged in as {profile.email}. Only {invitation.email} can
            accept this invitation.
          </p>
          <button
            type="button"
            className={secondaryButton}
            onClick={switchAccount}
            disabled={signingOut}
          >
            <FiLogIn aria-hidden="true" />
            {signingOut ? "Logging out…" : "Log in with another account"}
          </button>
        </div>
      ) : (
        <button
          type="button"
          className={`${primaryButton} mt-7 w-full`}
          onClick={join}
          disabled={accepting || profileQuery.isLoading}
        >
          <FiUsers aria-hidden="true" />
          {accepting ? "Joining…" : `Join ${invitation.businessName}`}
        </button>
      )}
    </PublicPage>
  );
};

export default AcceptInvitePage;
