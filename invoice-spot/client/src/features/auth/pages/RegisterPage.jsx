import { useState } from "react";
import { useDispatch } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { Formik, useField } from "formik";
import * as Yup from "yup";
import useTitle from "../../../hooks/useTitle";
import { useLoginUserMutation, useRegisterUserMutation } from "../authApiSlice";
import { logIn } from "../authSlice";
import AuthVisual from "../components/AuthVisual";
import {
  ArrowIcon,
  EyeIcon,
  GitHubIcon,
  GoogleIcon,
  GuestIcon,
  GUEST_CREDENTIALS,
  OAUTH_BASE,
} from "../components/authCommon";
import {
  PasswordStrength,
  auth,
  fieldError,
  passwordToggle,
  rise,
} from "../components/authForm";

const USERNAME_REGEX = /^[A-Za-z][A-Za-z0-9_-]{3,23}$/;

// Mirrors RegistrationService.isStrongPassword on the backend.
const registerSchema = Yup.object().shape({
  firstName: Yup.string().max(255).required("First name is required"),
  lastName: Yup.string().max(255).required("Last name is required"),
  username: Yup.string()
    .required("Username is required")
    .matches(
      USERNAME_REGEX,
      "Start with a letter; 4–24 letters, numbers, - or _",
    ),
  email: Yup.string()
    .email("Must be a valid email")
    .max(255)
    .required("Email is required"),
  password: Yup.string()
    .max(255)
    .required("Password is required")
    .min(8, "At least 8 characters")
    .matches(/\p{Lu}/u, "Add an uppercase letter")
    .matches(/\p{Ll}/u, "Add a lowercase letter")
    .matches(/\p{Nd}/u, "Add a number")
    .matches(/[^\p{Lu}\p{Ll}\p{Nd}]/u, "Add a symbol"),
  passwordConfirm: Yup.string()
    .required("Confirm your password")
    .oneOf([Yup.ref("password")], "Passwords must match"),
});

const delay = (d) => ({ "--d": `${d}s` });

const AuthField = ({
  label,
  name,
  type = "text",
  revealable = false,
  ...inputProps
}) => {
  const [field, meta] = useField(name);
  const [revealed, setRevealed] = useState(false);
  const invalid = Boolean(meta.touched && meta.error);
  const id = `register-${name}`;
  const input = (
    <input
      id={id}
      className={revealable ? auth.passwordInput : auth.input}
      type={revealable && revealed ? "text" : type}
      aria-invalid={invalid}
      {...field}
      {...inputProps}
    />
  );

  return (
    <div className={auth.field}>
      <label className={auth.label} htmlFor={id}>
        {label}
      </label>
      {revealable ? (
        <div className="relative">
          {input}
          <button
            type="button"
            className={passwordToggle}
            onClick={() => setRevealed((shown) => !shown)}
            aria-label={revealed ? "Hide password" : "Show password"}
          >
            <EyeIcon slashed={revealed} />
          </button>
        </div>
      ) : (
        input
      )}
      {invalid && <div className={fieldError}>{meta.error}</div>}
    </div>
  );
};

const RegisterPage = () => {
  useTitle("Sign Up - Invoice Spot");

  const navigate = useNavigate();
  const dispatch = useDispatch();

  const [registerUser, { isLoading: registering }] = useRegisterUserMutation();
  const [loginUser, { isLoading: loggingIn }] = useLoginUserMutation();

  const handleGuestLogin = async () => {
    try {
      const credentials = await loginUser(GUEST_CREDENTIALS).unwrap();
      dispatch(logIn({ ...credentials }));
      navigate("/dashboard");
    } catch (err) {
      toast.error(
        err?.data?.message || "Unable to open the demo. Please try again.",
      );
    }
  };

  return (
    <div className={auth.page}>
      <AuthVisual
        headline={["Start invoicing", "in minutes."]}
        subtext="Set up your account, verify your email, and start sending invoices."
        loaderLabel="Creating your workspace"
      />

      <section className={auth.formPanel}>
        <div className={auth.formWrapWide}>
          <Link to="/" className={auth.mobileBrand}>
            <span className={auth.brandMark}>IS</span>Invoice Spot
          </Link>
          <div className={auth.formHeadTight}>
            <h1 className={`${auth.title} ${rise}`} style={delay(0.15)}>
              Create your account
            </h1>
            <p className={`${auth.sub} ${rise}`} style={delay(0.22)}>
              Get started with Invoice Spot
            </p>
          </div>

          <Formik
            initialValues={{
              firstName: "",
              lastName: "",
              username: "",
              email: "",
              password: "",
              passwordConfirm: "",
            }}
            validationSchema={registerSchema}
            onSubmit={async (values, { setSubmitting }) => {
              try {
                const response = await registerUser(values).unwrap();
                toast.success(
                  response?.message ||
                    "Account created. Check your email to verify it.",
                );
                navigate("/");
              } catch (err) {
                toast.error(err?.data?.message || "Registration failed");
              } finally {
                setSubmitting(false);
              }
            }}
          >
            {({ handleSubmit, isSubmitting, values }) => {
              const busy = isSubmitting || registering || loggingIn;
              return (
                <form className={auth.form} noValidate onSubmit={handleSubmit}>
                  <div className={auth.fields}>
                    <div className={`${auth.row} ${rise}`} style={delay(0.3)}>
                      <AuthField
                        label="First name"
                        name="firstName"
                        placeholder="John"
                        autoComplete="given-name"
                      />
                      <AuthField
                        label="Last name"
                        name="lastName"
                        placeholder="Doe"
                        autoComplete="family-name"
                      />
                    </div>

                    <div className={`${auth.row} ${rise}`} style={delay(0.36)}>
                      <AuthField
                        label="Username"
                        name="username"
                        placeholder="john-doe"
                        autoComplete="username"
                      />
                      <AuthField
                        label="Email"
                        name="email"
                        type="email"
                        placeholder="you@business.com"
                        autoComplete="email"
                      />
                    </div>

                    <div className={`${auth.row} ${rise}`} style={delay(0.42)}>
                      <AuthField
                        label="Password"
                        name="password"
                        type="password"
                        revealable
                        placeholder="Create a password"
                        autoComplete="new-password"
                      />
                      <AuthField
                        label="Confirm password"
                        name="passwordConfirm"
                        type="password"
                        revealable
                        placeholder="Repeat password"
                        autoComplete="new-password"
                      />
                    </div>

                    <PasswordStrength
                      password={values.password}
                      className={`-mt-2 ${rise}`}
                      style={delay(0.46)}
                    />
                  </div>

                  <div className={auth.actions}>
                    <button
                      type="submit"
                      className={`${auth.primary} ${rise}`}
                      style={delay(0.5)}
                      disabled={busy}
                    >
                      {isSubmitting || registering
                        ? "Creating account…"
                        : "Create account"}
                    </button>
                    <p className={`${auth.terms} ${rise}`} style={delay(0.54)}>
                      By creating an account, you agree to the{" "}
                      <Link to="#">Terms of Service</Link> and{" "}
                      <Link to="#">Privacy Policy</Link>.
                    </p>
                  </div>

                  <div className={auth.alt}>
                    <div
                      className={`${auth.divider} ${rise}`}
                      style={delay(0.6)}
                    >
                      <div className={auth.dividerLine} />
                      <span className={auth.dividerText}>or continue with</span>
                      <div className={auth.dividerLine} />
                    </div>

                    <div
                      className={`${auth.socialThree} ${rise}`}
                      style={delay(0.66)}
                    >
                      <button
                        type="button"
                        className={`${auth.ghost} ${auth.tight}`}
                        onClick={() =>
                          window.open(`${OAUTH_BASE}/google`, "_self")
                        }
                      >
                        <GoogleIcon />
                        Google
                      </button>
                      <button
                        type="button"
                        className={`${auth.ghost} ${auth.tight}`}
                        onClick={() =>
                          window.open(`${OAUTH_BASE}/github`, "_self")
                        }
                      >
                        <GitHubIcon />
                        GitHub
                      </button>
                      <button
                        type="button"
                        className={`${auth.ghost} ${auth.tight}`}
                        onClick={handleGuestLogin}
                        disabled={busy}
                      >
                        <GuestIcon />
                        {loggingIn ? "Opening demo…" : "Guest"}
                      </button>
                    </div>
                  </div>

                  <div className={`${auth.foot} ${rise}`} style={delay(0.72)}>
                    <Link to="/login" className={auth.cta}>
                      <span className={auth.ctaText}>
                        Already have an account?
                      </span>
                      <span className={auth.ctaAction}>
                        Log in <ArrowIcon />
                      </span>
                    </Link>
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

export default RegisterPage;
