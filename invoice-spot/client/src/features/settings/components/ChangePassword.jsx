import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { FiKey } from "react-icons/fi";
import { toast } from "react-toastify";
import {
  Field,
  Panel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { panelNote, primaryButton } from "../../../components/workspace/pastel";
import { logIn } from "../../auth/authSlice";
import { useChangePasswordMutation } from "../securityApiSlice";

const EMPTY = { currentPassword: "", newPassword: "", confirmPassword: "" };

// A new password signs out every other device. This one stays signed in
// with the access token the change sends back. An account made by Google
// sign-in has no current password to type, so it gets a note instead.
const ChangePassword = ({ demo, hasPassword }) => {
  const [values, setValues] = useState(EMPTY);
  const [change, { isLoading }] = useChangePasswordMutation();
  const user = useSelector((state) => state.auth.user);
  const dispatch = useDispatch();
  const edit = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await change(values).unwrap();
      dispatch(logIn({ ...user, accessToken: result.accessToken }));
      setValues(EMPTY);
      toast.success(result.message);
    } catch (error) {
      toast.error(errorMessage(error, "Your password could not be changed."));
    }
  };
  const disabled = demo || isLoading;
  return (
    <Panel
      title="Password"
      description="Changing it signs out your other devices. This one stays signed in."
    >
      {!hasPassword ? (
        <p className={panelNote}>
          Your account has no password yet. To set one, use Forgot password on
          the sign-in page.
        </p>
      ) : (
        <form className="flex flex-col gap-5" onSubmit={submit}>
          <Field
            label="Current password"
            type="password"
            autoComplete="current-password"
            maxLength={128}
            required
            value={values.currentPassword}
            onChange={edit("currentPassword")}
            disabled={disabled}
          />
          <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
            <Field
              label="New password"
              type="password"
              autoComplete="new-password"
              maxLength={128}
              required
              value={values.newPassword}
              onChange={edit("newPassword")}
              disabled={disabled}
              hint="At least 8 characters, with an uppercase and a lowercase letter, a number and a symbol."
            />
            <Field
              label="Confirm new password"
              type="password"
              autoComplete="new-password"
              maxLength={128}
              required
              value={values.confirmPassword}
              onChange={edit("confirmPassword")}
              disabled={disabled}
            />
          </div>
          <div className="flex flex-wrap items-center justify-end gap-4 max-[700px]:[&>button]:flex-1">
            {demo && (
              <p className={`mr-auto ${panelNote}`}>
                This is a shared demo account, so its password can’t be changed.
              </p>
            )}
            <button type="submit" className={primaryButton} disabled={disabled}>
              <FiKey aria-hidden="true" />
              {isLoading ? "Changing…" : "Change password"}
            </button>
          </div>
        </form>
      )}
    </Panel>
  );
};

export default ChangePassword;
