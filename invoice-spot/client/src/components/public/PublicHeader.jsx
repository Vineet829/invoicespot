import { useEffect, useRef } from "react";
import { useSelector } from "react-redux";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { FiArrowUpRight, FiLogOut } from "react-icons/fi";
import { useLogoutUserMutation } from "../../features/auth/authApiSlice";
import {
  brandMark,
  brandMarkBase,
  buttonBase,
  iconButton,
  primaryButton,
} from "../workspace/pastel";

const navLink =
  "inline-flex min-h-11 items-center whitespace-nowrap rounded-xl px-3.5 text-[14px] transition-colors max-[480px]:px-2.5";
// Narrow phones drop the arrow so the header keeps to one line.
const headerButton = "max-[480px]:px-3 max-[400px]:[&>svg]:hidden";

// The header's colours: on the pastel canvas, or on the homepage's dark
// stage, where everything is white or lilac.
const TONES = {
  light: {
    brand: brandMark,
    link: `${navLink} text-spot-nav hover:bg-white/60 hover:text-spot-night`,
    button: `${primaryButton} ${headerButton}`,
    profile: "hover:bg-white/60",
    name: "text-spot-ink",
    icon: `${iconButton} size-10`,
  },
  dark: {
    brand: `${brandMarkBase} text-white [&>span]:text-spot-lilac`,
    link: `${navLink} text-[#cfc8ea] hover:bg-white/10 hover:text-white`,
    button: `${buttonBase} bg-white text-[#1b1430] hover:bg-[#ebe7f8] ${headerButton}`,
    profile: "hover:bg-white/10",
    name: "text-white",
    icon: "inline-flex size-10 shrink-0 items-center justify-center rounded-xl border border-transparent bg-transparent p-0 text-[#cfc8ea] transition-colors hover:enabled:bg-white/10 hover:enabled:text-white [&>svg]:size-[17px]",
  },
};

// The top of every public page. Signed-in visitors get the way back to
// their workspace, their profile and sign out, as in the workspace sidebar.
// signUp={false} drops the sign-up button where the page offers sign-up
// itself; signUpLabel names it. sections lists parts of the page to link
// to, as { id, label }, beside the logo: a link scrolls to its section,
// smoothly unless the visitor prefers less motion. tone="dark" dresses the
// header for a dark ground.
const PublicHeader = ({
  sections = [],
  signUp = true,
  signUpLabel = "Get started",
  tone = "light",
}) => {
  const { user } = useSelector((state) => state.auth);
  const navigate = useNavigate();
  const { hash, key } = useLocation();
  const [logoutUser, { isLoading: signingOut }] = useLogoutUserMutation();
  const look = TONES[tone];
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

  // Phones keep to Log in and the sign-up button; on the narrowest the
  // links take a second row, on the right.
  return (
    <header className="flex min-h-16 flex-wrap items-center gap-x-3 gap-y-1">
      <Link to="/" className={look.brand} aria-label="Invoice Spot home">
        Invoice Spot<span>.</span>
      </Link>
      <nav
        className="flex flex-1 flex-wrap items-center gap-2 max-[480px]:gap-1"
        aria-label="Main navigation"
      >
        {sections.length > 0 && (
          <span className="ml-5 flex items-center gap-1 max-[700px]:hidden">
            {sections.map(({ id, label }) => (
              <Link to={`#${id}`} className={look.link} key={id}>
                {label}
              </Link>
            ))}
          </span>
        )}
        <span className="ml-auto flex items-center gap-2 max-[480px]:gap-1">
          {user ? (
            <>
              <Link
                to="/profile"
                className={`flex min-h-11 items-center gap-2.5 rounded-xl pl-1.5 pr-3 transition-colors max-[600px]:pr-1.5 ${look.profile}`}
                aria-label={`Profile for ${name}`}
                title={name}
              >
                <span
                  className="grid size-8 shrink-0 place-items-center rounded-[11px] bg-avatar-lilac text-[13px] leading-none text-[#3f2f6e]"
                  aria-hidden="true"
                >
                  {initials}
                </span>
                <span
                  className={`max-w-[160px] truncate text-[14px] max-[600px]:hidden ${look.name}`}
                >
                  {name}
                </span>
              </Link>
              <button
                type="button"
                className={look.icon}
                onClick={signOut}
                disabled={signingOut}
                aria-label="Sign out"
                title="Sign out"
              >
                <FiLogOut aria-hidden="true" />
              </button>
              <Link to="/dashboard" className={look.button}>
                Dashboard <FiArrowUpRight aria-hidden="true" />
              </Link>
            </>
          ) : (
            <>
              <Link to="/login" className={look.link}>
                Log in
              </Link>
              {signUp && (
                <Link to="/register" className={look.button}>
                  {signUpLabel} <FiArrowUpRight aria-hidden="true" />
                </Link>
              )}
            </>
          )}
        </span>
      </nav>
    </header>
  );
};

export default PublicHeader;
