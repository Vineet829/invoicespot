import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:justify-center ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// The accounts and the audit log, as two views of Administration.
const AdminTabs = () => (
  <nav
    aria-label="Administration views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1 max-[700px]:basis-full"
  >
    <NavLink end to="/users" className={tab}>
      Users
    </NavLink>
    <NavLink to="/users/audit" className={tab}>
      Audit log
    </NavLink>
  </nav>
);

export default AdminTabs;
