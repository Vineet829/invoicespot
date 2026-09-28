import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { Formik } from "formik";
import * as Yup from "yup";
import useTitle from "../../../hooks/useTitle";
import { useLoginUserMutation } from "../authApiSlice";
import { logIn } from "../authSlice";
import AuthVisual from "../components/AuthVisual";
import { ArrowIcon, EyeIcon, GitHubIcon, GoogleIcon, GuestIcon, MailIcon, GUEST_CREDENTIALS, OAUTH_BASE } from "../components/authCommon";
import "../../../styles/auth.css";

const loginSchema = Yup.object().shape({
  email: Yup.string().email("Must be a valid email").max(255).required("Email is required"),
  password: Yup.string().max(255).required("Password is required"),
});

const delay = (d) => ({ "--d": `${d}s` });

const LoginPage = () => {
  useTitle("Login - Invoice Spot");

  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();

  const from = location.state?.from?.pathname || "/dashboard";

  const [showPassword, setShowPassword] = useState(false);
  const [loginUser, { isLoading, isSuccess }] = useLoginUserMutation();
  const [loginGuest, { isLoading: guestLoading }] = useLoginUserMutation();

  useEffect(() => {
    if (isSuccess) {
      navigate(from, { replace: true });
    }
  }, [isSuccess, navigate, from]);

  const handleGuestLogin = async () => {
    try {
      const credentials = await loginGuest(GUEST_CREDENTIALS).unwrap();
      dispatch(logIn({ ...credentials }));
      navigate(from, { replace: true });
    } catch (err) {
      toast.error(err?.data?.message || "Unable to open the demo. Please try again.");
    }
  };

  return (
    <div className="auth-page">
      <AuthVisual
        headline={["Your invoices,", "simplified."]}
        subtext="Create invoices, email PDFs, and track payments — all in one place."
        loaderLabel="Setting up your workspace"
      />

      <section className="auth-form-panel">
        <div className="auth-form-wrap">
          <Link to="/" className="auth-mobile-brand"><span className="auth-brand-mark">IS</span>Invoice Spot</Link>
          <div className="auth-form-head">
            <h1 className="auth-title auth-rise" style={delay(0.15)}>Welcome back</h1>
            <p className="auth-sub auth-rise" style={delay(0.22)}>Log in to your Invoice Spot account</p>
          </div>

          <Formik
            initialValues={{ email: "", password: "" }}
            validationSchema={loginSchema}
            onSubmit={async (values, { setStatus, setSubmitting }) => {
              try {
                const credentials = await loginUser(values).unwrap();
                dispatch(logIn({ ...credentials }));
                setStatus({ success: true });
              } catch (err) {
                toast.error(err?.data?.message || "Login failed");
                setStatus({ success: false });
              } finally {
                setSubmitting(false);
              }
            }}
          >
            {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => {
              const busy = isSubmitting || isLoading || guestLoading;
              const emailError = touched.email && errors.email;
              const passwordError = touched.password && errors.password;
              return (
                <form className="auth-form" noValidate onSubmit={handleSubmit}>
                  <div className="auth-fields">
                    <div className="auth-field auth-rise" style={delay(0.3)}>
                      <label className="auth-label" htmlFor="login-email">Email</label>
                      <input
                        id="login-email"
                        className="auth-input"
                        type="email"
                        name="email"
                        autoComplete="email"
                        placeholder="you@business.com"
                        value={values.email}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        aria-invalid={Boolean(emailError)}
                      />
                      {emailError && <div className="auth-error">{errors.email}</div>}
                    </div>

                    <div className="auth-field auth-rise" style={delay(0.36)}>
                      <div className="auth-label-row">
                        <label className="auth-label" htmlFor="login-password">Password</label>
                        <Link to="/reset_password_request" className="auth-link">Forgot password?</Link>
                      </div>
                      <div className="auth-pw">
                        <input
                          id="login-password"
                          className="auth-input"
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
                          className="auth-pw-toggle"
                          onClick={() => setShowPassword((shown) => !shown)}
                          aria-label={showPassword ? "Hide password" : "Show password"}
                        >
                          <EyeIcon slashed={showPassword} />
                        </button>
                      </div>
                      {passwordError && <div className="auth-error">{errors.password}</div>}
                    </div>
                  </div>

                  <button type="submit" className="auth-btn auth-btn-primary auth-rise" style={delay(0.42)} disabled={busy}>
                    {isSubmitting || isLoading ? "Logging in…" : "Log in"}
                  </button>

                  <div className="auth-alt">
                    <div className="auth-divider auth-rise" style={delay(0.48)}>
                      <div className="auth-divider-line" />
                      <span className="auth-divider-text">or continue with</span>
                      <div className="auth-divider-line" />
                    </div>

                    <div className="auth-social auth-rise" style={delay(0.54)}>
                      <button type="button" className="auth-btn auth-btn-ghost" onClick={() => window.open(`${OAUTH_BASE}/google`, "_self")}>
                        <GoogleIcon />
                        Google
                      </button>
                      <button type="button" className="auth-btn auth-btn-ghost" onClick={() => window.open(`${OAUTH_BASE}/github`, "_self")}>
                        <GitHubIcon />
                        GitHub
                      </button>
                    </div>

                    <div className="auth-divider auth-rise" style={delay(0.6)}>
                      <div className="auth-divider-line" />
                      <span className="auth-divider-text">or</span>
                      <div className="auth-divider-line" />
                    </div>

                    <button type="button" className="auth-btn auth-btn-ghost auth-rise" style={delay(0.66)} onClick={handleGuestLogin} disabled={busy}>
                      <GuestIcon />
                      {guestLoading ? "Opening demo…" : "Guest"}
                    </button>
                  </div>

                  <div className="auth-foot auth-rise" style={delay(0.72)}>
                    <Link to="/register" className="auth-cta">
                      <span className="auth-cta-text">New to Invoice Spot?</span>
                      <span className="auth-cta-action">Create an account <ArrowIcon /></span>
                    </Link>
                    <p className="auth-note">
                      <MailIcon />
                      <span>Didn't get the verification email? <Link to="/resend">Resend it</Link></span>
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
