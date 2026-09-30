import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { isExpired } from "react-jwt";
import { toast } from "react-toastify";
import { FiArrowUpRight, FiLogOut } from "react-icons/fi";
import { logOut } from "../../features/auth/authSlice";
import { useLogoutUserMutation } from "../../features/auth/authApiSlice";
import { brandMark, iconButton, primaryButton } from "../workspace/pastel";

const navLink =
  "inline-flex min-h-11 items-center whitespace-nowrap rounded-xl px-3.5 text-[14px] text-spot-nav transition-colors hover:bg-white/60 hover:text-spot-night max-[480px]:px-2.5";
// Narrow phones drop the arrow so the header keeps to one line.
const headerButton = `${primaryButton} max-[480px]:px-3 max-[400px]:[&>svg]:hidden`;

// The top of every public page. Signed-in visitors get the way back to
// their workspace, their profile and sign out, as in the workspace sidebar.
// signUp={false} drops Get started where the page offers sign-up itself.
const PublicHeader = ({ signUp = true }) => {
  const { user, googleToken } = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();
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

  useEffect(() => {
    if (googleToken && isExpired(googleToken)) {
      dispatch(logOut());
      navigate("/login");
      toast.warning("Your session has expired. Please log in again.");
    }
  }, [dispatch, googleToken, navigate]);

  const signOut = async () => {
    await logoutUser();
    navigate("/login", { replace: true });
  };

  return (
    <header className="flex min-h-16 items-center justify-between gap-4">
      <Link to="/" className={brandMark} aria-label="Invoice Spot home">
        Invoice Spot<span>.</span>
      </Link>
      <nav
        className="flex items-center gap-2 max-[480px]:gap-1"
        aria-label="Main navigation"
      >
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
                Get started <FiArrowUpRight aria-hidden="true" />
              </Link>
            )}
          </>
        )}
      </nav>
    </header>
  );
};

export default PublicHeader;
