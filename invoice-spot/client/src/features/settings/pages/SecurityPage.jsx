import useTitle from "../../../hooks/useTitle";
import {
  PageHeader,
  WorkspacePage,
} from "../../../components/workspace/Workspace";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import ChangePassword from "../components/ChangePassword";
import RecentActivity from "../components/RecentActivity";
import SettingsTabs from "../components/SettingsTabs";
import SignedInDevices from "../components/SignedInDevices";
import TwoFactor from "../components/TwoFactor";

const SecurityPage = () => {
  useTitle("Security — Invoice Spot");
  const profile = useGetUserProfileQuery().data?.userProfile;
  const demo = profile?.demo;
  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="How you sign in, and where your account is signed in."
        actions={<SettingsTabs />}
      />
      <ChangePassword
        demo={demo}
        hasPassword={profile?.hasPassword !== false}
      />
      <TwoFactor demo={demo} />
      <SignedInDevices demo={demo} />
      <RecentActivity demo={demo} />
    </WorkspacePage>
  );
};

export default SecurityPage;
