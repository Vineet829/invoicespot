import { useState } from "react";
import { toast } from "react-toastify";
import { useCompleteTwoFactorMutation } from "../authApiSlice";
import { auth, rise } from "./authForm";

const delay = (d) => ({ "--d": `${d}s` });
const TIMED_OUT = "Your sign-in timed out";

// The second step of a two-factor sign-in, in the log-in form's place: a
// code from the authenticator app, or one of the saved recovery codes.
const TwoFactorStep = ({ challenge, onBack, onSignedIn }) => {
  const [recovery, setRecovery] = useState(false);
  const [code, setCode] = useState("");
  const [complete, { isLoading }] = useCompleteTwoFactorMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      onSignedIn(await complete({ challenge, code }).unwrap());
    } catch (error) {
      const message = error?.data?.message || "That code didn't work";
      toast.error(message);
      if (message.startsWith(TIMED_OUT)) onBack();
    }
  };
  return (
    <>
      <div className={auth.formHead}>
        <h1 className={`${auth.title} ${rise}`} style={delay(0.05)}>
          Two-step sign-in
        </h1>
        <p className={`${auth.sub} ${rise}`} style={delay(0.1)}>
          {recovery
            ? "Enter one of the recovery codes you saved when you turned on two-factor sign-in."
            : "Enter the 6-digit code from your authenticator app."}
        </p>
      </div>
      <form className={auth.form} noValidate onSubmit={submit}>
        <div className={`${auth.field} ${rise}`} style={delay(0.15)}>
          <label className={auth.label} htmlFor="login-code">
            {recovery ? "Recovery code" : "Code"}
          </label>
          <input
            key={recovery ? "recovery" : "app"}
            id="login-code"
            className={auth.input}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            autoComplete={recovery ? "off" : "one-time-code"}
            inputMode={recovery ? "text" : "numeric"}
            maxLength={recovery ? 11 : 6}
            placeholder={recovery ? "xxxxx-xxxxx" : "123456"}
            spellCheck={false}
            // Only this field is on the page once the password is accepted.
            autoFocus
          />
        </div>
        <button
          type="submit"
          className={`${auth.primary} ${rise}`}
          style={delay(0.2)}
          disabled={isLoading || !code.trim()}
        >
          {isLoading ? "Checking…" : "Verify"}
        </button>
        <div
          className={`flex flex-wrap items-center justify-between gap-3 ${rise}`}
          style={delay(0.25)}
        >
          <button
            type="button"
            className={`${auth.link} bg-transparent p-0`}
            onClick={() => {
              setRecovery((current) => !current);
              setCode("");
            }}
          >
            {recovery ? "Use the code from your app" : "Use a recovery code"}
          </button>
          <button
            type="button"
            className={`${auth.link} bg-transparent p-0`}
            onClick={onBack}
          >
            Back to log in
          </button>
        </div>
      </form>
    </>
  );
};

export default TwoFactorStep;
