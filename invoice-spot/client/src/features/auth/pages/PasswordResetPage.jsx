import { Formik } from "formik";
import { useEffect, useState } from "react";
import { FiLock } from "react-icons/fi";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "react-toastify";
import * as Yup from "yup";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import { errorMessage } from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  inputControl,
  primaryButton,
} from "../../../components/workspace/pastel";
import { EyeIcon } from "../components/authCommon";
import {
  PasswordStrength,
  fieldError,
  passwordToggle,
} from "../components/authForm";
import { useResetPasswordMutation } from "../authApiSlice";

const PasswordField = ({
  id,
  label,
  name,
  value,
  error,
  onChange,
  onBlur,
  disabled,
}) => {
  const [shown, setShown] = useState(false);
  return (
    <div className="flex flex-col gap-2">
      <label className={fieldLabel} htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          className={`${inputControl} pr-11`}
          type={shown ? "text" : "password"}
          name={name}
          value={value}
          onChange={onChange}
          onBlur={onBlur}
          autoComplete="new-password"
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `helper-text-${id}` : undefined}
          disabled={disabled}
        />
        <button
          type="button"
          className={passwordToggle}
          onClick={() => setShown((current) => !current)}
          aria-label={shown ? "Hide password" : "Show password"}
        >
          <EyeIcon slashed={shown} />
        </button>
      </div>
      {error && (
        <p id={`helper-text-${id}`} className={fieldError}>
          {error}
        </p>
      )}
    </div>
  );
};

const PasswordResetPage = () => {
  useTitle("Request Reset Password");
  const navigate = useNavigate();
  // The emailed link carries the reset token and the account it belongs to.
  const [searchParams] = useSearchParams();

  const [resetPassword, { data, isLoading, isSuccess }] =
    useResetPasswordMutation();

  useEffect(() => {
    if (isSuccess) {
      navigate("/login");
      const message = data.message;
      toast.success(message);
    }
  }, [data, isSuccess, navigate]);

  return (
    <PublicPage>
      <CardHeading icon={FiLock} title="Choose a new password">
        Enter your new password to finish the reset process.
      </CardHeading>
      <Formik
        initialValues={{ password: "", passwordConfirm: "" }}
        validationSchema={Yup.object().shape({
          password: Yup.string().max(255).required("Password is required"),
          passwordConfirm: Yup.string()
            .oneOf([Yup.ref("password")], "Passwords Must Match")
            .required("Please confirm your password"),
        })}
        onSubmit={async (values, { setStatus, setSubmitting }) => {
          try {
            await resetPassword({
              ...values,
              emailToken: searchParams.get("emailToken"),
              userId: searchParams.get("userId"),
            }).unwrap();
            setStatus({ success: true });
            setSubmitting(false);
          } catch (err) {
            // A server that can't be reached sends no message of its own.
            toast.error(
              errorMessage(
                err,
                "Your password could not be reset. Please try again.",
              ),
            );
            setStatus({ success: false });
            setSubmitting(false);
          }
        }}
      >
        {({
          errors,
          handleBlur,
          handleChange,
          handleSubmit,
          touched,
          values,
        }) => (
          <form
            className="mt-7 flex flex-col gap-5"
            noValidate
            autoComplete="off"
            onSubmit={handleSubmit}
          >
            <PasswordField
              id="password-signup"
              label="Password"
              name="password"
              value={values.password}
              error={touched.password && errors.password}
              onChange={handleChange}
              onBlur={handleBlur}
              disabled={isLoading}
            />
            <PasswordStrength password={values.password} />
            <PasswordField
              id="passwordConfirm-signup"
              label="Confirm password"
              name="passwordConfirm"
              value={values.passwordConfirm}
              error={touched.passwordConfirm && errors.passwordConfirm}
              onChange={handleChange}
              onBlur={handleBlur}
              disabled={isLoading}
            />
            <button
              type="submit"
              className={`${primaryButton} mt-1 w-full`}
              disabled={
                (!values.password && !values.passwordConfirm) || isLoading
              }
            >
              {isLoading ? "Resetting…" : "Reset password"}
            </button>
          </form>
        )}
      </Formik>
    </PublicPage>
  );
};

export default PasswordResetPage;
