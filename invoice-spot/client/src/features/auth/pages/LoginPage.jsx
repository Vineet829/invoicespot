import { useState } from "react";
import { useDispatch } from "react-redux";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { Formik } from "formik";
import * as Yup from "yup";
import useTitle from "../../../hooks/useTitle";
import { useLoginUserMutation } from "../authApiSlice";
import { logIn } from "../authSlice";
import AuthVisual from "../components/AuthVisual";
import TwoFactorStep from "../components/TwoFactorStep";
import {
  ArrowIcon,
  EyeIcon,
  GitHubIcon,
  GoogleIcon,
  GuestIcon,
  MailIcon,
  GUEST_CREDENTIALS,
  OAUTH_BASE,
} from "../components/authCommon";
import { auth, fieldError, passwordToggle, rise } from "../components/authForm";

const loginSchema = Yup.object().shape({
  email: Yup.string()
    .email("Must be a valid email")
    .max(254, "Must be a valid email")
    .required("Email is required"),
  password: Yup.string()
    .max(128, "At most 128 characters")
    .required("Password is required"),
});

const delay = (d) => ({ "--d": `${d}s` });

const LoginPage = () => {
  useTitle("Login - Invoice Spot");

  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();

  const from = location.state?.from?.pathname || "/dashboard";

  const [showPassword, setShowPassword] = useState(false);
  // With two-factor sign-in on, a correct password earns a challenge that a
  // code completes.
  const [challenge, setChallenge] = useState(null);
  const [loginUser, { isLoading }] = useLoginUserMutation();
  const [loginGuest, { isLoading: guestLoading }] = useLoginUserMutation();

  const signedIn = (credentials) => {
    dispatch(logIn({ ...credentials }));
    navigate(from, { replace: true });
  };

  const handleGuestLogin = async () => {
    try {
      signedIn(await loginGuest(GUEST_CREDENTIALS).unwrap());
    } catch (err) {
      toast.error(
        err?.data?.message || "Unable to open the demo. Please try again.",
      );
    }
  };

  // Both steps share the page, so the visual stays as it is between them.
  const visual = (
    <AuthVisual
      headline={["Your invoices,", "simplified."]}
      subtext="Create invoices, email PDFs, and track payments&nbsp;— all in one place."
      loaderLabel="Setting up your workspace"
    />
  );

  if (challenge) {
    return (
      <div className={auth.page}>
        {visual}

        <section className={auth.formPanel}>
          <div className={auth.formWrap}>
            <Link to="/" className={auth.mobileBrand}>
              <span className={auth.brandMark}>IS</span>Invoice Spot
            </Link>
            <TwoFactorStep
              challenge={challenge}
              onBack={() => setChallenge(null)}
              onSignedIn={signedIn}
            />
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className={auth.page}>
      {visual}

      <section className={auth.formPanel}>
        <div className={auth.formWrap}>
          <Link to="/" className={auth.mobileBrand}>
            <span className={auth.brandMark}>IS</span>Invoice Spot
          </Link>
          <div className={auth.formHead}>
            <h1 className={`${auth.title} ${rise}`} style={delay(0.15)}>
              Welcome back
            </h1>
            <p className={`${auth.sub} ${rise}`} style={delay(0.22)}>
              Log in to your Invoice Spot account
            </p>
          </div>

          <Formik
            initialValues={{ email: "", password: "" }}
            validationSchema={loginSchema}
            onSubmit={async (values, { setStatus, setSubmitting }) => {
              try {
                const result = await loginUser(values).unwrap();
                setStatus({ success: true });
                if (result.twoFactorRequired) setChallenge(result.challenge);
                else signedIn(result);
              } catch (err) {
                toast.error(err?.data?.message || "Login failed");
                setStatus({ success: false });
              } finally {
                setSubmitting(false);
              }
            }}
          >
            {({
              errors,
              handleBlur,
              handleChange,
              handleSubmit,
              isSubmitting,
              touched,
              values,
            }) => {
              const busy = isSubmitting || isLoading || guestLoading;
              const emailError = touched.email && errors.email;
              const passwordError = touched.password && errors.password;
              return (
                <form className={auth.form} noValidate onSubmit={handleSubmit}>
                  <div className={auth.fields}>
                    <div className={`${auth.field} ${rise}`} style={delay(0.3)}>
                      <label className={auth.label} htmlFor="login-email">
                        Email
                      </label>
                      <input
                        id="login-email"
                        className={auth.input}
                        type="email"
                        name="email"
                        autoComplete="email"
                        placeholder="you@business.com"
                        value={values.email}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        aria-invalid={Boolean(emailError)}
                      />
                      {emailError && (
                        <div className={fieldError}>{errors.email}</div>
                      )}
                    </div>

                    <div
                      className={`${auth.field} ${rise}`}
                      style={delay(0.36)}
                    >
                      <div className={auth.labelRow}>
                        <label className={auth.label} htmlFor="login-password">
                          Password
                        </label>
                        <Link
                          to="/reset_password_request"
                          className={auth.link}
                        >
                          Forgot password?
                        </Link>
                      </div>
                      <div className="relative">
                        <input
                          id="login-password"
                          className={auth.passwordInput}
                          type={showPassword ? "text" : "password"}
                          name="password"
                          autoComplete="current-password"
                          placeholder="Enter your password"
                          value={values.password}
                          onChange={handleChange}
                          onBlur={handleBlur}
                          aria-invalid={Boolean(passwordError)}
                        />
                        <button
                          type="button"
                          className={passwordToggle}
                          onClick={() => setShowPassword((shown) => !shown)}
                          aria-label={
                            showPassword ? "Hide password" : "Show password"
                          }
                        >
                          <EyeIcon slashed={showPassword} />
                        </button>
                      </div>
                      {passwordError && (
                        <div className={fieldError}>{errors.password}</div>
                      )}
                    </div>
                  </div>

                  <button
                    type="submit"
                    className={`${auth.primary} ${rise}`}
                    style={delay(0.42)}
                    disabled={busy}
                  >
                    {isSubmitting || isLoading ? "Logging in…" : "Log in"}
                  </button>

                  <div className={auth.alt}>
                    <div
                      className={`${auth.divider} ${rise}`}
                      style={delay(0.48)}
                    >
                      <div className={auth.dividerLine} />
                      <span className={auth.dividerText}>or continue with</span>
                      <div className={auth.dividerLine} />
                    </div>

                    <div
                      className={`${auth.social} ${rise}`}
                      style={delay(0.54)}
                    >
                      <button
                        type="button"
                        className={auth.ghost}
                        onClick={() =>
                          window.open(`${OAUTH_BASE}/google`, "_self")
                        }
                      >
                        <GoogleIcon />
                        Google
                      </button>
                      <button
                        type="button"
                        className={auth.ghost}
                        onClick={() =>
                          window.open(`${OAUTH_BASE}/github`, "_self")
                        }
                      >
                        <GitHubIcon />
                        GitHub
                      </button>
                    </div>

                    <div
                      className={`${auth.divider} ${rise}`}
                      style={delay(0.6)}
                    >
                      <div className={auth.dividerLine} />
                      <span className={auth.dividerText}>or</span>
                      <div className={auth.dividerLine} />
                    </div>

                    <button
                      type="button"
                      className={`${auth.ghost} ${rise}`}
                      style={delay(0.66)}
                      onClick={handleGuestLogin}
                      disabled={busy}
                    >
                      <GuestIcon />
                      {guestLoading ? "Opening demo…" : "Guest"}
                    </button>
                  </div>

                  <div className={`${auth.foot} ${rise}`} style={delay(0.72)}>
                    <Link to="/register" className={auth.cta}>
                      <span className={auth.ctaText}>New to Invoice Spot?</span>
                      <span className={auth.ctaAction}>
                        Create an account <ArrowIcon />
                      </span>
                    </Link>
                    <p className={auth.note}>
                      <MailIcon />
                      <span>
                        Didn't get the verification email?{" "}
                        <Link to="/resend">Resend it</Link>
                      </span>
                    </p>
                  </div>
                </form>
              );
            }}
          </Formik>
        </div>
      </section>
    </div>
  );
};

export default LoginPage;
