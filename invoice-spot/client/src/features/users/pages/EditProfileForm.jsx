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
import {
  primaryButton,
  sectionTitle,
} from "../../../components/workspace/pastel";
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
const MAX_LOGO_BYTES = 2 * 1024 * 1024;

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
    if (file.size > MAX_LOGO_BYTES) {
      toast.error("Logos can be at most 2 MB.");
      event.target.value = "";
      return;
    }
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
      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
        <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
          <Panel
            title="Personal details"
            description="The details associated with your account."
          >
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="First name"
                maxLength={60}
                name="firstName"
                required
                autoComplete="given-name"
                value={values.firstName}
                onChange={change("firstName")}
                disabled={busy}
              />
              <Field
                label="Last name"
                maxLength={60}
                name="lastName"
                required
                autoComplete="family-name"
                value={values.lastName}
                onChange={change("lastName")}
                disabled={busy}
              />
              <Field
                label="Username"
                maxLength={60}
                name="username"
                required
                autoComplete="username"
                value={values.username}
                onChange={change("username")}
                disabled={busy}
              />
              <Field
                label="Phone number"
                maxLength={30}
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
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="Business name"
                maxLength={255}
                name="businessName"
                required
                autoComplete="organization"
                value={values.businessName}
                onChange={change("businessName")}
                disabled={busy}
                className="col-span-full"
              />
              <Field
                label="Address"
                maxLength={255}
                name="address"
                required
                autoComplete="street-address"
                value={values.address}
                onChange={change("address")}
                disabled={busy}
                className="col-span-full"
              />
              <Field
                label="City"
                maxLength={255}
                name="city"
                autoComplete="address-level2"
                value={values.city}
                onChange={change("city")}
                disabled={busy}
              />
              <Field
                label="Country"
                maxLength={255}
                name="country"
                autoComplete="country-name"
                value={values.country}
                onChange={change("country")}
                disabled={busy}
              />
            </div>
          </Panel>
          <Panel title="Business logo">
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="Logo URL"
                maxLength={500}
                name="avatar"
                value={values.avatar}
                onChange={change("avatar")}
                disabled={busy}
                className="col-span-full"
              />
              <Field
                label="Choose your logo"
                type="file"
                accept="image/*"
                hint="An image of up to 2 MB."
                onChange={uploadLogo}
                disabled={busy}
                className="col-span-full"
              />
              {uploading && (
                <p
                  role="status"
                  className="col-span-full text-[13px] text-spot-violet"
                >
                  Uploading your logo…
                </p>
              )}
            </div>
          </Panel>
          <div className="flex justify-end max-[700px]:[&>*]:flex-1">
            <button type="submit" className={primaryButton} disabled={busy}>
              <FiCheck aria-hidden="true" />
              {saving ? "Saving…" : "Save changes"}
            </button>
          </div>
        </div>
        <aside className="min-w-0 max-[1359px]:hidden min-[1360px]:sticky min-[1360px]:top-[30px]">
          <div className="rounded-[30px] border border-white/85 bg-white/50 p-6 shadow-panel-soft backdrop-blur-[16px] compact:p-[18px] [&>p]:mt-3 [&>p]:text-[13.5px] [&>p]:leading-[1.7] [&>p]:text-spot-body [&>svg]:mb-4 [&>svg]:size-7 [&>svg]:stroke-[1.4] [&>svg]:text-spot-violet">
            <FiUser aria-hidden="true" />
            <h2 className={sectionTitle}>A familiar face for your business.</h2>
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
