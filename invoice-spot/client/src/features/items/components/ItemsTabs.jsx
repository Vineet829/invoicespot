import { NavLink } from "react-router-dom";

const tab = ({ isActive }) =>
  `inline-flex min-h-9 items-center whitespace-nowrap rounded-[10px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:justify-center ${isActive ? "bg-spot-night font-medium text-white" : "text-spot-body hover:bg-white"}`;

// The items, and what each sold over a period, as views of one page, in
// the page header like the Documents views. On a phone it takes a row of
// its own.
const ItemsTabs = () => (
  <nav
    aria-label="Items views"
    className="inline-flex items-center gap-1 rounded-[14px] border border-spot-violet/15 bg-white/60 p-1 max-[700px]:min-w-full max-[700px]:flex-wrap"
  >
    <NavLink end to="/items" className={tab}>
      All items
    </NavLink>
    <NavLink to="/items/sales" className={tab}>
      Sales by item
    </NavLink>
  </nav>
);

export default ItemsTabs;
