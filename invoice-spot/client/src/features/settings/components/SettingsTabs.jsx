import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:justify-center ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// The business defaults and the account's security, as two views of
// Settings.
const SettingsTabs = () => (
  <nav
    aria-label="Settings views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1 max-[700px]:basis-full"
  >
    <NavLink end to="/settings" className={tab}>
      Business
    </NavLink>
    <NavLink to="/settings/security" className={tab}>
      Security
    </NavLink>
  </nav>
);

export default SettingsTabs;
