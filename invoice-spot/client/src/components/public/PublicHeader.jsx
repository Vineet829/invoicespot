import { useEffect, useRef } from "react";
import { useSelector } from "react-redux";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { FiArrowUpRight, FiLogOut } from "react-icons/fi";
import { useLogoutUserMutation } from "../../features/auth/authApiSlice";
import { brandMark, iconButton, primaryButton } from "../workspace/pastel";

const navLink =
  "inline-flex min-h-11 items-center whitespace-nowrap rounded-xl px-3.5 text-[14px] text-spot-nav transition-colors hover:bg-white/60 hover:text-spot-night max-[480px]:px-2.5";
// Phones keep to Log in and the sign-up button.
const sectionLink = `${navLink} max-[700px]:hidden`;
// Narrow phones drop the arrow so the header keeps to one line.
const headerButton = `${primaryButton} max-[480px]:px-3 max-[400px]:[&>svg]:hidden`;

// The top of every public page. Signed-in visitors get the way back to
// their workspace, their profile and sign out, as in the workspace sidebar.
// signUp={false} drops the sign-up button where the page offers sign-up
// itself; signUpLabel names it. sections lists parts of the page to link
// to, as { id, label }: a link scrolls to its section, smoothly unless the
// visitor prefers less motion.
const PublicHeader = ({
  sections = [],
  signUp = true,
  signUpLabel = "Get started",
}) => {
  const { user } = useSelector((state) => state.auth);
  const navigate = useNavigate();
  const { hash, key } = useLocation();
  const [logoutUser, { isLoading: signingOut }] = useLogoutUserMutation();
  const name =
    [user?.firstName, user?.lastName].filter(Boolean).join(" ") ||
    user?.username ||
    "My account";
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  // Each visit to a section's address scrolls to it, so a link works again
  // after the visitor has scrolled away, and the page's own address, from
  // a section, goes back to the top. A visitor arriving at a section waits
  // for the fonts, so the section sits where it will stay.
  const arrival = useRef(key);
  useEffect(() => {
    if (!sections.length) return undefined;
    const arriving = key === arrival.current;
    if (arriving && !hash) return undefined;
    const behavior = window.matchMedia?.("(prefers-reduced-motion: reduce)")
      .matches
      ? "auto"
      : "smooth";
    const scroll = () => {
      if (!hash) window.scrollTo({ top: 0, behavior });
      else document.getElementById(hash.slice(1))?.scrollIntoView({ behavior });
    };
    if (!arriving) {
      scroll();
      return undefined;
    }
    let cancelled = false;
    (document.fonts?.ready ?? Promise.resolve()).then(() => {
      if (!cancelled) scroll();
    });
    return () => {
      cancelled = true;
    };
  }, [sections.length, hash, key]);

  const signOut = async () => {
    await logoutUser();
    navigate("/login", { replace: true });
  };

  // On the narrowest phones the links take a second row, on the right.
  return (
    <header className="flex min-h-16 flex-wrap items-center justify-between gap-x-3 gap-y-1">
      <Link to="/" className={brandMark} aria-label="Invoice Spot home">
        Invoice Spot<span>.</span>
      </Link>
      <nav
        className="ml-auto flex items-center gap-2 max-[480px]:gap-1"
        aria-label="Main navigation"
      >
        {sections.map(({ id, label }) => (
          <Link to={`#${id}`} className={sectionLink} key={id}>
            {label}
          </Link>
        ))}
        {user ? (
          <>
            <Link
              to="/profile"
              className="flex min-h-11 items-center gap-2.5 rounded-xl pl-1.5 pr-3 transition-colors hover:bg-white/60 max-[600px]:pr-1.5"
              aria-label={`Profile for ${name}`}
              title={name}
            >
              <span
                className="grid size-8 shrink-0 place-items-center rounded-[11px] bg-avatar-lilac text-[12px] leading-none text-[#3f2f6e]"
                aria-hidden="true"
              >
                {initials}
              </span>
              <span className="max-w-[160px] truncate text-[14px] text-spot-ink max-[600px]:hidden">
                {name}
              </span>
            </Link>
            <button
              type="button"
              className={`${iconButton} size-10`}
              onClick={signOut}
              disabled={signingOut}
              aria-label="Sign out"
              title="Sign out"
            >
              <FiLogOut aria-hidden="true" />
            </button>
            <Link to="/dashboard" className={headerButton}>
              Dashboard <FiArrowUpRight aria-hidden="true" />
            </Link>
          </>
        ) : (
          <>
            <Link to="/login" className={navLink}>
              Log in
            </Link>
            {signUp && (
              <Link to="/register" className={headerButton}>
                {signUpLabel} <FiArrowUpRight aria-hidden="true" />
              </Link>
            )}
          </>
        )}
      </nav>
    </header>
  );
};

export default PublicHeader;
