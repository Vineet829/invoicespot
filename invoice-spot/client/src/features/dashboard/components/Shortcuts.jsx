import { Link } from "react-router-dom";
import { FiCreditCard, FiFileText, FiUserPlus } from "react-icons/fi";
import { RefusalNote, useBusiness } from "../../business/permissions";

// A white key, with its icon on a lilac tile; a disabled one greys out.
const key =
  "group inline-flex h-10 items-center gap-2 whitespace-nowrap rounded-[13px] border border-white/95 bg-white/[.78] pl-3 pr-[15px] text-[13.5px] text-spot-night shadow-[0_8px_18px_-14px_#3c346473] transition-colors hover:bg-white disabled:cursor-default disabled:bg-white/40 disabled:text-spot-disabled disabled:opacity-100 coarse:min-h-11";
const tile =
  "grid size-6 shrink-0 place-items-center rounded-lg bg-[#ece8fb] text-spot-violet group-disabled:bg-[#2327350d] group-disabled:text-spot-disabled [&>svg]:size-3.5 [&>svg]:stroke-[1.7]";

const SHORTCUTS = [
  { to: "/create-doc", label: "New invoice", icon: FiFileText },
  { to: "/expenses?new=1", label: "Record an expense", icon: FiCreditCard },
  { to: "/create-customer", label: "Add a client", icon: FiUserPlus },
];

// The overview's ways to start the work that comes up most: an invoice, an
// expense, a client. A role that can't make them sees them disabled, each
// pointing to the one note that gives the reason. On a narrow screen they
// wrap.
const Shortcuts = () => {
  const refusal = useBusiness().reason("EDIT_RECORDS");
  return (
    <nav aria-label="Shortcuts" className="mt-4 flex flex-wrap gap-2.5">
      {SHORTCUTS.map(({ to, label, icon: Icon }) => {
        const content = (
          <>
            <span className={tile} aria-hidden="true">
              <Icon />
            </span>
            {label}
          </>
        );
        return refusal ? (
          <button
            key={to}
            type="button"
            className={key}
            disabled
            title={refusal}
            aria-describedby="shortcuts-refusal"
          >
            {content}
          </button>
        ) : (
          <Link key={to} to={to} className={key}>
            {content}
          </Link>
        );
      })}
      <RefusalNote id="shortcuts-refusal" reason={refusal} />
    </nav>
  );
};

export default Shortcuts;
