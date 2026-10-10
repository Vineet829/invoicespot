import { useState } from "react";
import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { FiBriefcase, FiEdit2, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import { logOut } from "../../auth/authSlice";
import { baseApiSlice } from "../../api/baseApiSlice";
import {
  useGetUserProfileQuery,
  useDeleteMyAccountMutation,
} from "../usersApiSlice";
import { useGetBusinessQuery } from "../../business/businessApiSlice";
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
import { buttonBase, sectionTitle } from "../../../components/workspace/pastel";

// The danger zone is already peach, so its button is white.
const deleteAccountButton = `${buttonBase} border border-overdue-icon/25 bg-white/80 text-overdue-strong hover:enabled:bg-white disabled:bg-white/40 disabled:text-overdue-muted`;
// "A", "A and B", "A, B and C".
const listed = (names) =>
  names.length < 2
    ? names.join("")
    : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;

const ProfilePage = () => {
  useTitle("Profile — Invoice Spot");
  const query = useGetUserProfileQuery();
  const profile = query.data?.userProfile;
  const business = useGetBusinessQuery().data?.business;
  const shared = profile?.sharedBusinesses || [];
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
        description="Your account, and the business you work in."
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
              <div className="mb-6 flex items-center gap-5 border-b border-[#23273512] pb-6 max-[700px]:gap-4">
                <span
                  className="grid size-16 shrink-0 place-items-center rounded-[20px] bg-avatar-lilac font-display text-[22px] font-light text-avatar-lilac-ink max-[700px]:size-14 max-[700px]:rounded-[18px] max-[700px]:text-[19px]"
                  aria-hidden="true"
                >
                  {[profile.firstName?.[0], profile.lastName?.[0]]
                    .filter(Boolean)
                    .join("") || "IS"}
                </span>
                <div className="min-w-0">
                  <h2 className="font-display text-[28px] font-light leading-[1.15] tracking-[-0.04em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[23px]">
                    {[profile.firstName, profile.lastName]
                      .filter(Boolean)
                      .join(" ") || profile.username}
                  </h2>
                  <p className="mt-1 text-[13.5px] text-spot-muted [overflow-wrap:anywhere]">
                    {profile.email}
                  </p>
                </div>
              </div>
              <InfoGrid
                items={[
                  ["Username", profile.username],
                  ["Logged in with", profile.provider],
                ]}
              />
            </Panel>
            {business && (
              <Panel
                title="Business details"
                description="What your documents show. Change them in Settings."
                aside={
                  <ActionLink to="/settings/business" secondary>
                    <FiBriefcase aria-hidden="true" /> Business details
                  </ActionLink>
                }
              >
                <InfoGrid
                  items={[
                    ["Business name", business.name],
                    ["Email", business.email],
                    ["Phone number", business.phoneNumber],
                    [
                      "Address",
                      [
                        business.address,
                        business.city,
                        [business.state, business.postalCode]
                          .filter(Boolean)
                          .join(" "),
                        business.country,
                      ]
                        .filter(Boolean)
                        .join(", "),
                    ],
                  ]}
                />
              </Panel>
            )}
            <section className="flex items-center justify-between gap-6 rounded-[30px] border border-white/90 bg-overdue/60 p-6 compact:p-[18px] max-[700px]:flex-col max-[700px]:items-stretch max-[700px]:rounded-3xl max-[700px]:p-[18px]">
              <div className="min-w-0">
                <h2 className={`${sectionTitle} text-overdue-ink`}>
                  Delete account
                </h2>
                <p className="mt-1.5 text-[13.5px] text-overdue-muted">
                  {profile.demo
                    ? "The shared demo account can’t be deleted."
                    : shared.length
                      ? `You own ${listed(shared)}, which others work in. Hand it over or remove them first, in Settings → Team.`
                      : "Permanently delete your Invoice Spot account and your business."}
                </p>
              </div>
              <button
                type="button"
                className={deleteAccountButton}
                onClick={() => setOpen(true)}
                disabled={profile.demo || shared.length > 0}
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
        This permanently deletes your account and your business, with all its
        documents, clients and items. This action cannot be undone.
      </ConfirmDialog>
    </WorkspacePage>
  );
};
export default ProfilePage;
