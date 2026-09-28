import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FiCheck, FiUser } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useGetUserProfileQuery,
  useUpdateUserProfileMutation,
} from "../usersApiSlice";
import { useUploadProfileLogoMutation } from "../../workspace/workspaceApiSlice";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  Field,
  LoadingState,
  ErrorState,
  errorMessage,
} from "../../../components/workspace/Workspace";
const keys = [
  "firstName",
  "lastName",
  "username",
  "phoneNumber",
  "businessName",
  "address",
  "city",
  "country",
  "avatar",
];
const ProfileFields = ({ profile }) => {
  const navigate = useNavigate();
  const [values, setValues] = useState(() =>
    Object.fromEntries(keys.map((key) => [key, profile[key] || ""])),
  );
  const [save, { isLoading: saving }] = useUpdateUserProfileMutation();
  const [upload, { isLoading: uploading }] = useUploadProfileLogoMutation();
  const busy = saving || uploading;
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await save(values).unwrap();
      toast.success(result.message || "Profile updated.");
      navigate("/profile");
    } catch (error) {
      toast.error(errorMessage(error, "Your profile could not be saved."));
    }
  };
  const uploadLogo = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const body = new FormData();
    body.append("logo", file);
    try {
      const url = await upload(body).unwrap();
      setValues((current) => ({ ...current, avatar: url }));
      toast.success("Logo uploaded. Save your changes to update your profile.");
    } catch (error) {
      toast.error(errorMessage(error, "Your logo could not be uploaded."));
    }
    event.target.value = "";
  };
  return (
    <form onSubmit={submit}>
      <div className="workspace-form-layout">
        <div className="workspace-form-main">
          <Panel
            title="Personal details"
            description="The details associated with your account."
          >
            <div className="workspace-form-grid">
              <Field
                label="First name"
                name="firstName"
                required
                autoComplete="given-name"
                value={values.firstName}
                onChange={change("firstName")}
                disabled={busy}
              />
              <Field
                label="Last name"
                name="lastName"
                required
                autoComplete="family-name"
                value={values.lastName}
                onChange={change("lastName")}
                disabled={busy}
              />
              <Field
                label="Username"
                name="username"
                required
                autoComplete="username"
                value={values.username}
                onChange={change("username")}
                disabled={busy}
              />
              <Field
                label="Phone number"
                name="phoneNumber"
                type="tel"
                required
                autoComplete="tel"
                value={values.phoneNumber}
                onChange={change("phoneNumber")}
                disabled={busy}
                hint="Include your country code."
              />
            </div>
          </Panel>
          <Panel
            title="Business details"
            description="Your business information appears on your documents."
          >
            <div className="workspace-form-grid">
              <Field
                label="Business name"
                name="businessName"
                required
                autoComplete="organization"
                value={values.businessName}
                onChange={change("businessName")}
                disabled={busy}
                className="workspace-span-full"
              />
              <Field
                label="Address"
                name="address"
                required
                autoComplete="street-address"
                value={values.address}
                onChange={change("address")}
                disabled={busy}
                className="workspace-span-full"
              />
              <Field
                label="City"
                name="city"
                autoComplete="address-level2"
                value={values.city}
                onChange={change("city")}
                disabled={busy}
              />
              <Field
                label="Country"
                name="country"
                autoComplete="country-name"
                value={values.country}
                onChange={change("country")}
                disabled={busy}
              />
            </div>
          </Panel>
          <Panel title="Business logo">
            <div className="workspace-form-grid">
              <Field
                label="Logo URL"
                name="avatar"
                value={values.avatar}
                onChange={change("avatar")}
                disabled={busy}
                className="workspace-span-full"
              />
              <Field
                label="Choose your logo"
                type="file"
                accept="image/*"
                onChange={uploadLogo}
                disabled={busy}
                className="workspace-span-full"
              />
              {uploading && (
                <p role="status" className="workspace-loading-label">
                  Uploading your logo…
                </p>
              )}
            </div>
          </Panel>
          <div className="workspace-form-actions">
            <button type="submit" className="workspace-button" disabled={busy}>
              <FiCheck aria-hidden="true" />
              {saving ? "Saving…" : "Save changes"}
            </button>
          </div>
        </div>
        <aside className="workspace-form-aside">
          <div className="workspace-panel-body">
            <FiUser aria-hidden="true" />
            <h2>A familiar face for your business.</h2>
            <p>
              Keep your contact details and business logo current so your
              documents are ready to send.
            </p>
          </div>
        </aside>
      </div>
    </form>
  );
};
const EditProfileForm = () => {
  useTitle("Edit profile — Invoice Spot");
  const query = useGetUserProfileQuery();
  return (
    <WorkspacePage section="Profile">
      <PageHeader
        back
        title="Edit profile"
        description="Keep your account and business information up to date."
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        query.data?.userProfile && (
          <ProfileFields profile={query.data.userProfile} />
        )
      )}
    </WorkspacePage>
  );
};
export default EditProfileForm;
