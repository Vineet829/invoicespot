import { useState } from "react";
import { Link } from "react-router-dom";
import { FiCheck, FiChevronDown, FiPlus } from "react-icons/fi";
import { toast } from "react-toastify";
import usePageVisible from "../../../hooks/usePageVisible";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  fieldLabel,
  inputControl,
  panelNote,
  primaryButton,
  secondaryButton,
  textLink,
} from "../../../components/workspace/pastel";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import {
  useCreateBusinessMutation,
  useGetBusinessesQuery,
  useSwitchBusinessMutation,
} from "../businessApiSlice";
import useEnterBusiness from "../useEnterBusiness";

export const ROLE_LABELS = {
  OWNER: "Owner",
  ADMIN: "Admin",
  MEMBER: "Member",
  VIEWER: "Viewer",
};

const initialsOf = (name) =>
  (name || "")
    .split(/\s+/)
    .filter((part) => /[\p{L}\p{N}]/u.test(part[0] || ""))
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "B";

const row = (active) =>
  `flex w-full items-center gap-3 rounded-[14px] border px-3 py-2.5 text-left transition-colors disabled:opacity-60 ${active ? "border-white bg-white shadow-panel-soft" : "border-transparent bg-transparent hover:bg-[#5b47a00a]"}`;

const trigger =
  "flex max-w-full items-center gap-1 rounded-md border-0 bg-transparent p-0 text-left text-[14.5px] text-spot-muted transition-colors hover:text-spot-night coarse:min-h-11 [&>svg]:size-3.5 [&>svg]:shrink-0";

// The businesses someone works in, as a list to switch between, with a way
// to start another. The shell opens it from the line under the account's
// name, which names the business this tab works in. Like the bell, it checks
// for news every minute while the tab is in view: a dot says another
// business has unread notifications, and the list counts them. The bell
// counts the business this tab works in.
const BusinessSwitcher = ({ label }) => {
  const [open, setOpen] = useState(false);
  const [starting, setStarting] = useState(false);
  const [name, setName] = useState("");
  const visible = usePageVisible();
  const query = useGetBusinessesQuery(undefined, {
    pollingInterval: visible ? 60000 : 0,
  });
  const businesses = query.data?.businesses || [];
  const elsewhere = businesses.reduce(
    (sum, business) => (business.active ? sum : sum + (business.unread || 0)),
    0,
  );
  const demo = useGetUserProfileQuery().data?.userProfile?.demo;
  const [switchTo, { isLoading: switching }] = useSwitchBusinessMutation();
  const [create, { isLoading: creating }] = useCreateBusinessMutation();
  const enter = useEnterBusiness();
  const busy = switching || creating;
  const close = () => {
    setOpen(false);
    setStarting(false);
    setName("");
  };
  const choose = async (business) => {
    if (business.active) {
      close();
      return;
    }
    try {
      await switchTo(business._id).unwrap();
      close();
      toast.success(`You’re working in ${business.name}.`);
      enter(business._id);
    } catch (error) {
      toast.error(error?.data?.message || "That business could not be opened.");
    }
  };
  const start = async (event) => {
    event.preventDefault();
    try {
      const business = await create({ name }).unwrap();
      close();
      toast.success(`${business.name} is ready.`);
      enter(business._id);
    } catch (error) {
      toast.error(error?.data?.message || "The business could not be started.");
    }
  };
  return (
    <>
      <button
        type="button"
        className={trigger}
        onClick={() => {
          setOpen(true);
          query.refetch();
        }}
        aria-haspopup="dialog"
        aria-label={`${label}. Switch business${elsewhere ? `, ${elsewhere} unread in other businesses` : ""}`}
        title="Switch business"
      >
        <span className="min-w-0 truncate">{label}</span>
        <FiChevronDown aria-hidden="true" />
        {elsewhere > 0 && (
          <span
            className="size-2 shrink-0 rounded-full bg-spot-coral"
            aria-hidden="true"
          />
        )}
      </button>
      <Dialog
        open={open}
        title="Your businesses"
        onClose={close}
        busy={busy}
        closeLabel="Close businesses"
      >
        <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
          {businesses.map((business) => (
            <li key={business._id}>
              <button
                type="button"
                className={row(business.active)}
                onClick={() => choose(business)}
                disabled={busy}
                aria-current={business.active ? "true" : undefined}
                data-autofocus={business.active ? true : undefined}
              >
                {business.logo ? (
                  <img
                    className="size-9 shrink-0 rounded-xl bg-white object-contain"
                    src={business.logo}
                    alt=""
                  />
                ) : (
                  <span
                    className="grid size-9 shrink-0 place-items-center rounded-xl bg-avatar-lilac text-[13px] text-[#3f2f6e]"
                    aria-hidden="true"
                  >
                    {initialsOf(business.name)}
                  </span>
                )}
                <span className="min-w-0 flex-1">
                  <strong className="block truncate text-[14.5px] font-medium text-spot-ink">
                    {business.name}
                  </strong>
                  <small className="block text-[13px] text-spot-muted">
                    {ROLE_LABELS[business.role] || business.role}
                  </small>
                </span>
                {business.active ? (
                  <FiCheck
                    className="size-[18px] shrink-0 text-spot-violet"
                    aria-hidden="true"
                  />
                ) : (
                  business.unread > 0 && (
                    <span className="grid h-6 min-w-6 shrink-0 place-items-center rounded-full bg-spot-violet/[.12] px-1.5 text-[13px] text-spot-violet">
                      {business.unread}
                      <span className="sr-only"> unread</span>
                    </span>
                  )
                )}
              </button>
            </li>
          ))}
        </ul>
        {!demo &&
          (starting ? (
            <form className="mt-5 flex flex-col gap-3" onSubmit={start}>
              <label className={fieldLabel} htmlFor="new-business-name">
                New business name
              </label>
              <input
                id="new-business-name"
                className={inputControl}
                required
                maxLength={255}
                autoComplete="organization"
                value={name}
                onChange={(event) => setName(event.target.value)}
                disabled={busy}
                autoFocus
              />
              <div className="flex flex-wrap justify-end gap-2">
                <button
                  type="button"
                  className={secondaryButton}
                  onClick={() => setStarting(false)}
                  disabled={busy}
                >
                  Cancel
                </button>
                <button type="submit" className={primaryButton} disabled={busy}>
                  {creating ? "Starting…" : "Start business"}
                </button>
              </div>
            </form>
          ) : (
            <button
              type="button"
              className={`${secondaryButton} mt-5 w-full`}
              onClick={() => setStarting(true)}
              disabled={busy}
            >
              <FiPlus aria-hidden="true" /> Start a business
            </button>
          ))}
        <p className={`mt-4 ${panelNote}`}>
          Each business keeps its own clients, documents and settings.{" "}
          <Link className={textLink} to="/settings/business" onClick={close}>
            Business details
          </Link>
        </p>
      </Dialog>
    </>
  );
};

export default BusinessSwitcher;
