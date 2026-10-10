import { useCallback, useEffect, useRef, useState } from "react";
import { useDispatch } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import useTitle from "../../../hooks/useTitle";
import { pendingInvitePath } from "../../business/pendingInvite";
import { useRefreshSessionMutation } from "../authApiSlice";
import { logIn } from "../authSlice";
import AuthVisual from "../components/AuthVisual";
import TwoFactorStep from "../components/TwoFactorStep";
import { auth, rise } from "../components/authForm";

const delay = (d) => ({ "--d": `${d}s` });

// With two-factor sign-in on, the server sends the challenge after the #.
// Only reads here: StrictMode calls a state initializer twice.
const hashChallenge = () =>
  new URLSearchParams(window.location.hash.slice(1)).get("challenge") || null;

// Where Google sign-in comes back to. Either the server has started a session
// in its refresh cookie, which gives an access token the way a reload
// restores one, or it has sent a two-factor challenge that a code completes.
const OAuthCallbackPage = () => {
  useTitle("Signing in - Invoice Spot");

  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [challenge] = useState(hashChallenge);
  const [refreshSession] = useRefreshSessionMutation();
  // StrictMode runs effects twice in development; one sign-in is enough.
  const started = useRef(false);

  // An invitation opened before signing in waits for the account, as it
  // does on the log-in page.
  const signedIn = useCallback(
    (credentials) => {
      dispatch(logIn({ ...credentials }));
      navigate(pendingInvitePath() || "/dashboard", { replace: true });
    },
    [dispatch, navigate],
  );

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (challenge) {
      // The challenge and a code sign in, so it is kept out of the history.
      window.history.replaceState(
        window.history.state,
        "",
        window.location.pathname,
      );
      return;
    }
    const finish = async () => {
      try {
        signedIn(await refreshSession().unwrap());
      } catch {
        navigate("/login?oauth_error=failed", { replace: true });
      }
    };
    finish();
  }, [challenge, navigate, refreshSession, signedIn]);

  return (
    <div className={auth.page}>
      <AuthVisual
        headline={["Your invoices,", "simplified."]}
        subtext="Create invoices, email PDFs, and track payments&nbsp;— all in one place."
        loaderLabel="Setting up your workspace"
      />

      <section className={auth.formPanel}>
        <div className={auth.formWrap}>
          <Link to="/" className={auth.mobileBrand}>
            <span className={auth.brandMark}>IS</span>Invoice Spot
          </Link>
          {challenge ? (
            <TwoFactorStep
              challenge={challenge}
              onBack={() => navigate("/login", { replace: true })}
              onSignedIn={signedIn}
            />
          ) : (
            <div className={auth.formHead} role="status">
              <h1 className={`${auth.title} ${rise}`} style={delay(0.05)}>
                Signing you in…
              </h1>
              <p className={`${auth.sub} ${rise}`} style={delay(0.1)}>
                Just a moment while we open your workspace.
              </p>
            </div>
          )}
        </div>
      </section>
    </div>
  );
};

export default OAuthCallbackPage;
