import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center whitespace-nowrap rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:justify-center ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// The clients, and what each was billed, paid and owes over a period, as
// views of one page, in the page header like the Documents views. On a
// phone it takes a row of its own.
const ClientsTabs = () => (
  <nav
    aria-label="Clients views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1 max-[700px]:min-w-full max-[700px]:flex-wrap"
  >
    <NavLink end to="/customers" className={tab}>
      All clients
    </NavLink>
    <NavLink to="/customers/sales" className={tab}>
      Sales by customer
    </NavLink>
  </nav>
);

export default ClientsTabs;
