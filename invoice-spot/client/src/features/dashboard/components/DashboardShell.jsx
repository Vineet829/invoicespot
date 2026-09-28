import { useEffect, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { isExpired } from "react-jwt";
import {
  FiArrowUpRight,
  FiCalendar,
  FiChevronRight,
  FiFileText,
  FiGrid,
  FiLogOut,
  FiMenu,
  FiShield,
  FiUser,
  FiUsers,
  FiX,
} from "react-icons/fi";
import { toast } from "react-toastify";
import { logOut } from "../../auth/authSlice";
import { useLogoutUserMutation } from "../../auth/authApiSlice";
import useAuthUser from "../../../hooks/useAuthUser";

const DashboardShell = ({
  children,
  documentCount,
  customerCount,
  section = "Overview",
  appearance,
}) => {
  const { user, googleToken } = useSelector((state) => state.auth);
  const { isAdmin } = useAuthUser();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [logoutUser, { isLoading: signingOut }] = useLogoutUserMutation();
  const [menuOpen, setMenuOpen] = useState(false);
  const dialogRef = useRef(null);
  const menuButtonRef = useRef(null);
  const desktopWidth = appearance === "pastel" ? 1200 : 961;
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

  useEffect(() => {
    const dialog = dialogRef.current;
    if (menuOpen && !dialog.open) dialog.showModal();
    if (!menuOpen && dialog.open) dialog.close();
    if (!menuOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const desktop = window.matchMedia(`(min-width: ${desktopWidth}px)`);
    const closeOnDesktop = () => {
      if (desktop.matches) setMenuOpen(false);
    };
    desktop.addEventListener("change", closeOnDesktop);
    return () => {
      document.body.style.overflow = previousOverflow;
      desktop.removeEventListener("change", closeOnDesktop);
    };
  }, [menuOpen, desktopWidth]);

  const signOut = async () => {
    setMenuOpen(false);
    await logoutUser();
    navigate("/login", { replace: true });
  };

  const navigation = [
    { to: "/dashboard", label: "Overview", icon: FiGrid },
    {
      to: "/documents",
      label: "Documents",
      icon: FiFileText,
      count: documentCount,
    },
    { to: "/customers", label: "Clients", icon: FiUsers, count: customerCount },
    { to: "/profile", label: "Profile", icon: FiUser },
    ...(isAdmin
      ? [{ to: "/users", label: "Administration", icon: FiShield }]
      : []),
  ];

  const sidebar = (mobile = false) => (
    <>
      <div className="dashboard-sidebar-brand">
        <Link to="/" className="dashboard-brand">
          Invoice Spot<span>.</span>
        </Link>
        {mobile && (
          <button
            className="dashboard-icon-button"
            onClick={() => setMenuOpen(false)}
            aria-label="Close navigation"
          >
            <FiX />
          </button>
        )}
      </div>
      <div className="dashboard-sidebar-label">WORKSPACE</div>
      <nav
        className="dashboard-navigation"
        aria-label={
          mobile ? "Mobile workspace navigation" : "Workspace navigation"
        }
      >
        {navigation.map(({ to, label, icon: Icon, count }) => (
          <Link
            key={to}
            to={to}
            aria-current={section === label ? "page" : undefined}
            onClick={() => setMenuOpen(false)}
            className={`dashboard-nav-link${section === label ? " is-active" : ""}`}
          >
            <Icon aria-hidden="true" />
            <span>{label}</span>
            {count != null && (
              <span className="dashboard-nav-count">{count}</span>
            )}
          </Link>
        ))}
      </nav>
      <div className="dashboard-sidebar-bottom">
        <Link className="dashboard-home-link" to="/" title="Back to homepage">
          Back to homepage <FiArrowUpRight aria-hidden="true" />
        </Link>
        <div className="dashboard-account">
          <Link
            to="/profile"
            className="dashboard-account-link"
            aria-label={`Profile for ${name}`}
            title={name}
          >
            <span className="dashboard-avatar" aria-hidden="true">
              {initials}
            </span>
            <span>
              <strong>{name}</strong>
              <small>{isAdmin ? "Administrator" : "Personal workspace"}</small>
            </span>
          </Link>
          <button
            className="dashboard-icon-button"
            onClick={signOut}
            disabled={signingOut}
            aria-label="Sign out"
            title="Sign out"
          >
            <FiLogOut aria-hidden="true" />
          </button>
        </div>
      </div>
    </>
  );

  return (
    <div
      className={`dashboard-app${appearance === "pastel" ? " dashboard-pastel" : ""}`}
    >
      <a className="dashboard-skip-link" href="#dashboard-content">
        Skip to {section.toLowerCase()}
      </a>
      <div
        className="dashboard-frame"
        style={appearance === "pastel" ? undefined : { display: "contents" }}
      >
        <aside className="dashboard-sidebar">{sidebar()}</aside>
        <dialog
          className="dashboard-sidebar-dialog"
          id="dashboard-navigation-dialog"
          ref={dialogRef}
          onClose={() => {
            setMenuOpen(false);
            if (window.matchMedia(`(max-width: ${desktopWidth - 1}px)`).matches)
              menuButtonRef.current?.focus({ preventScroll: true });
          }}
          aria-label="Workspace navigation"
          onClick={(event) => {
            if (event.target === event.currentTarget) setMenuOpen(false);
          }}
        >
          <div className="dashboard-mobile-sidebar">{sidebar(true)}</div>
        </dialog>
        <div className="dashboard-workspace">
          <header className="dashboard-topbar">
            <div className="dashboard-topbar-start">
              <button
                className="dashboard-icon-button dashboard-menu-button"
                ref={menuButtonRef}
                onClick={() => setMenuOpen(true)}
                aria-label="Open navigation"
                aria-expanded={menuOpen}
                aria-haspopup="dialog"
                aria-controls="dashboard-navigation-dialog"
              >
                <FiMenu aria-hidden="true" />
              </button>
              <span className="dashboard-breadcrumb">
                Workspace <FiChevronRight aria-hidden="true" />
                <strong>{section}</strong>
              </span>
              <Link to="/" className="dashboard-mobile-brand">
                Invoice Spot
              </Link>
            </div>
            <span className="dashboard-today">
              <FiCalendar aria-hidden="true" />
              {new Date().toLocaleDateString("en", {
                day: "numeric",
                month: "short",
                year: "numeric",
              })}
            </span>
          </header>
          <div
            className="dashboard-content"
            id="dashboard-content"
            tabIndex={-1}
          >
            {children}
          </div>
          <footer className="dashboard-footer">
            <span>© {new Date().getFullYear()} Invoice Spot</span>
            <span>Made for independent business.</span>
          </footer>
        </div>
      </div>
    </div>
  );
};

export default DashboardShell;
