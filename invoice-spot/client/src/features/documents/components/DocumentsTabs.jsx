import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:justify-center ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// Documents and their recurring schedules, as two views of one page. It
// sits in the page header, so the documents table keeps its height; on a
// phone it takes a row of its own.
const DocumentsTabs = () => (
  <nav
    aria-label="Documents views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1 max-[700px]:basis-full"
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
