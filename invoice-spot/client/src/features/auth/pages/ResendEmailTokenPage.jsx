import { Formik } from "formik";
import { useEffect } from "react";
import { FiSend } from "react-icons/fi";
import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import * as Yup from "yup";
import useTitle from "../../../hooks/useTitle";
import PublicPage, { CardHeading } from "../../../components/public/PublicPage";
import { errorMessage } from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  inputControl,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { fieldError } from "../components/authForm";
import { useResendVerifyEmailMutation } from "../authApiSlice";

const ResendEmailTokenPage = () => {
  useTitle("Resend Verification Email");

  const navigate = useNavigate();
  const goBack = () => navigate(-1);

  const [resendVerifyEmail, { data, isLoading, isSuccess }] =
    useResendVerifyEmailMutation();

  useEffect(() => {
    if (isSuccess) {
      navigate("/");
      const message = data.message;
      toast.success(message);
    }
  }, [data, isSuccess, navigate]);

  return (
    <PublicPage>
      <CardHeading icon={FiSend} title="Resend verification email">
        Enter the email you signed up with and we’ll send you a new verification
        link.
      </CardHeading>
      <Formik
        initialValues={{ email: "" }}
        validationSchema={Yup.object().shape({
          email: Yup.string()
            .email("Must be a valid email")
            .max(255)
            .required("Email is required"),
        })}
        onSubmit={async (values, { setStatus, setSubmitting }) => {
          try {
            await resendVerifyEmail(values).unwrap();

            setStatus({ success: true });
            setSubmitting(false);
          } catch (err) {
            // A server that can't be reached sends no message of its own.
            toast.error(
              errorMessage(
                err,
                "A new link could not be requested. Please try again.",
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
        }) => {
          const emailError = touched.email && errors.email;
          return (
            <form
              className="mt-7 flex flex-col gap-6"
              noValidate
              autoComplete="off"
              onSubmit={handleSubmit}
            >
              <div className="flex flex-col gap-2">
                <label className={fieldLabel} htmlFor="email-signup">
                  Email address
                  <span className="text-spot-violet" aria-hidden="true">
                    {" *"}
                  </span>
                </label>
                <input
                  id="email-signup"
                  className={inputControl}
                  type="email"
                  name="email"
                  value={values.email}
                  onBlur={handleBlur}
                  onChange={handleChange}
                  placeholder="you@business.com"
                  aria-required="true"
                  aria-invalid={Boolean(emailError)}
                  aria-describedby={
                    emailError ? "helper-text-email-signup" : undefined
                  }
                  disabled={isLoading}
                />
                {emailError && (
                  <p id="helper-text-email-signup" className={fieldError}>
                    {errors.email}
                  </p>
                )}
              </div>
              <div className="flex flex-col gap-2.5">
                <button
                  type="submit"
                  className={`${primaryButton} w-full`}
                  disabled={!values.email || isLoading}
                >
                  <FiSend aria-hidden="true" />
                  {isLoading ? "Sending…" : "Resend verification email"}
                </button>
                <button
                  type="button"
                  className={`${secondaryButton} w-full`}
                  onClick={goBack}
                >
                  Go back
                </button>
              </div>
            </form>
          );
        }}
      </Formik>
    </PublicPage>
  );
};

export default ResendEmailTokenPage;
