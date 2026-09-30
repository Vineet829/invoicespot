import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// Documents and their recurring schedules, as two views of one page. It
// sits in the page header, so the documents table keeps its height.
const DocumentsTabs = () => (
  <nav
    aria-label="Documents views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1"
  >
    <NavLink end to="/documents" className={tab}>
      All documents
    </NavLink>
    <NavLink to="/recurring" className={tab}>
      Recurring
    </NavLink>
  </nav>
);

export default DocumentsTabs;
