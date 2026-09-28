import { useState } from "react";
import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { FiEdit2, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { logOut } from "../../auth/authSlice";
import { baseApiSlice } from "../../api/baseApiSlice";
import {
  useGetUserProfileQuery,
  useDeleteMyAccountMutation,
} from "../usersApiSlice";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  InfoGrid,
  ConfirmDialog,
  LoadingState,
  ErrorState,
  errorMessage,
} from "../../../components/workspace/Workspace";
const ProfilePage = () => {
  useTitle("Profile — Invoice Spot");
  const query = useGetUserProfileQuery();
  const profile = query.data?.userProfile;
  const [open, setOpen] = useState(false);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [remove, { isLoading: deleting }] = useDeleteMyAccountMutation();
  const deleteAccount = async () => {
    try {
      await remove().unwrap();
      setOpen(false);
      dispatch(logOut());
      dispatch(baseApiSlice.util.resetApiState());
      navigate("/login", { replace: true });
      toast.success("Your account has been deleted.");
    } catch (error) {
      toast.error(errorMessage(error, "Your account could not be deleted."));
    }
  };
  return (
    <WorkspacePage section="Profile">
      <PageHeader
        title="Your profile"
        description="Your account and business details, in one place."
        actions={
          profile && (
            <ActionLink to="/edit-profile">
              <FiEdit2 aria-hidden="true" /> Edit profile
            </ActionLink>
          )
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        profile && (
          <>
            <Panel>
              <div className="workspace-identity">
                {profile.avatar ? (
                  <img src={profile.avatar} alt="Your business logo" />
                ) : (
                  <span aria-hidden="true">
                    {[profile.firstName?.[0], profile.lastName?.[0]]
                      .filter(Boolean)
                      .join("") || "IS"}
                  </span>
                )}
                <div>
                  <h2>
                    {[profile.firstName, profile.lastName]
                      .filter(Boolean)
                      .join(" ")}
                  </h2>
                  <p>{profile.email}</p>
                </div>
              </div>
              <InfoGrid
                items={[
                  ["First name", profile.firstName],
                  ["Last name", profile.lastName],
                  ["Username", profile.username],
                  ["Email address", profile.email],
                  ["Logged in with", profile.provider],
                ]}
              />
            </Panel>
            <Panel title="Business details">
              <InfoGrid
                items={[
                  ["Business name", profile.businessName],
                  ["Phone number", profile.phoneNumber],
                  ["Address", profile.address],
                  ["City", profile.city],
                  ["Country", profile.country],
                ]}
              />
            </Panel>
            <section className="workspace-danger-zone">
              <div>
                <h2>Delete account</h2>
                <p>Permanently delete your Invoice Spot account.</p>
              </div>
              <button
                className="workspace-button is-danger"
                onClick={() => setOpen(true)}
              >
                <FiTrash2 aria-hidden="true" /> Delete account
              </button>
            </section>
          </>
        )
      )}
      <ConfirmDialog
        open={open}
        title="Delete your account?"
        confirmLabel="Delete account"
        onConfirm={deleteAccount}
        onClose={() => setOpen(false)}
        busy={deleting}
      >
        This permanently deletes your account. This action cannot be undone.
      </ConfirmDialog>
    </WorkspacePage>
  );
};
export default ProfilePage;
