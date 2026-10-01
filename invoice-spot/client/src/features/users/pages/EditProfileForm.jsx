import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FiCheck, FiUser } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useGetUserProfileQuery,
  useUpdateUserProfileMutation,
} from "../usersApiSlice";
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
  panelNote,
  primaryButton,
  sectionTitle,
  textLink,
} from "../../../components/workspace/pastel";
const keys = ["firstName", "lastName", "username"];

const ProfileFields = ({ profile }) => {
  const navigate = useNavigate();
  const [values, setValues] = useState(() =>
    Object.fromEntries(keys.map((key) => [key, profile[key] || ""])),
  );
  const [save, { isLoading: saving }] = useUpdateUserProfileMutation();
  // Everyone who tries the demo shares its account, so nobody renames it.
  const locked = saving || profile.demo;
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
                disabled={locked}
              />
              <Field
                label="Last name"
                maxLength={60}
                name="lastName"
                required
                autoComplete="family-name"
                value={values.lastName}
                onChange={change("lastName")}
                disabled={locked}
              />
              <Field
                label="Username"
                maxLength={60}
                name="username"
                required
                autoComplete="username"
                value={values.username}
                onChange={change("username")}
                disabled={locked}
              />
            </div>
          </Panel>
          <div className="flex flex-wrap items-center justify-end gap-4 max-[700px]:[&>button]:flex-1">
            {profile.demo && (
              <p className={`mr-auto ${panelNote}`}>
                This is a shared demo account, so its profile can’t be changed.
              </p>
            )}
            <button type="submit" className={primaryButton} disabled={locked}>
              <FiCheck aria-hidden="true" />
              {saving ? "Saving…" : "Save changes"}
            </button>
          </div>
        </div>
        <aside className="min-w-0 max-[1359px]:hidden min-[1360px]:sticky min-[1360px]:top-[30px]">
          <div className="rounded-[30px] border border-white/85 bg-white/50 p-6 shadow-panel-soft backdrop-blur-[16px] compact:p-[18px] [&>p]:mt-3 [&>p]:text-[13.5px] [&>p]:leading-[1.7] [&>p]:text-spot-body [&>svg]:mb-4 [&>svg]:size-7 [&>svg]:stroke-[1.4] [&>svg]:text-spot-violet">
            <FiUser aria-hidden="true" />
            <h2 className={sectionTitle}>Just you.</h2>
            <p>
              Your name and username are yours alone. The details your documents
              carry belong to your business, in{" "}
              <Link className={textLink} to="/settings/business">
                Settings
              </Link>
              .
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
        description="Keep your account details up to date."
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
