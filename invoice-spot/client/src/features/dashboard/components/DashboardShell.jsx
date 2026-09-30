import { useEffect, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { isExpired } from "react-jwt";
import {
  FiArrowUpRight,
  FiBarChart2,
  FiChevronRight,
  FiFileText,
  FiGrid,
  FiLogOut,
  FiMenu,
  FiPackage,
  FiSearch,
  FiSettings,
  FiShield,
  FiUser,
  FiUsers,
  FiX,
} from "react-icons/fi";
import { toast } from "react-toastify";
import { logOut } from "../../auth/authSlice";
import { useLogoutUserMutation } from "../../auth/authApiSlice";
import useAuthUser from "../../../hooks/useAuthUser";
import { brandMark, iconButton } from "../../../components/workspace/pastel";
import CommandPalette from "../../shell/CommandPalette";
import NotificationBell from "../../notifications/NotificationBell";
import BusinessSwitcher, {
  ROLE_LABELS,
} from "../../business/components/BusinessSwitcher";
import { useGetBusinessesQuery } from "../../business/businessApiSlice";

const navLink =
  "relative flex min-h-12 items-center gap-3 rounded-2xl px-3.5 text-[16px] transition-[color,background-color] duration-150 hover:bg-white/70 hover:text-spot-night [&>svg]:size-5 [&>svg]:shrink-0 [&>svg]:stroke-[1.5]";

const content =
  "block min-h-dvh w-full px-8 pb-12 pt-[30px] outline-none compact:px-6 compact:pt-5 max-[1359px]:px-7 max-[1359px]:pt-7 max-[960px]:min-h-[calc(100dvh_-_68px)] max-[960px]:px-6 max-[960px]:pt-6 max-[700px]:px-4 max-[700px]:pb-10 max-[700px]:pt-5";

// The signed-in shell: sidebar navigation, the drawer below 961px, and the
// top bar that opens it.
const ui = {
  // dashboard-app scopes the workspace's reset in src/index.css. The
  // sidebar widens with the window, from 260px to 360px, only as fast as
  // the overview's middle column can spare the room.
  app: "dashboard-app grid min-h-dvh grid-cols-[var(--sidebar-width)_minmax(0,1fr)] bg-spot-canvas bg-canvas p-0 font-ui font-normal leading-[normal] tracking-normal text-spot-ink [--sidebar-width:clamp(260px,calc(40vw_-_306px),360px)] [color-scheme:light] max-[960px]:grid-cols-[minmax(0,1fr)]",
  skipLink:
    "dashboard-skip-link left-[calc(var(--sidebar-width)+16px)] bg-white text-spot-night max-[960px]:left-6 coarse:inline-flex coarse:min-h-11 coarse:items-center",
  sidebar:
    "sticky top-0 flex h-dvh min-h-[540px] flex-col border-r border-white/70 bg-white/[.34] px-4 pb-[18px] pt-[30px] backdrop-blur-[18px] max-[960px]:hidden",
  brandRow: "flex items-center justify-between px-3",
  brand: brandMark,
  search:
    "mt-8 flex min-h-11 w-full items-center gap-3 rounded-2xl border border-white/85 bg-white/50 px-3.5 text-left text-[14.5px] text-spot-muted transition-colors hover:bg-white/80 hover:text-spot-night [&>svg]:size-[18px] [&>svg]:shrink-0 [&>svg]:stroke-[1.6]",
  shortcut:
    "ml-auto rounded-md border border-[#23273524] px-1.5 py-0.5 font-ui text-[11px] text-spot-muted",
  navigation: "mt-4 flex flex-col gap-1.5",
  navLink: `${navLink} text-spot-nav`,
  navLinkActive: `${navLink} bg-white font-medium text-spot-ink shadow-nav [&>svg]:text-spot-violet`,
  sidebarBottom: "mt-auto flex flex-col gap-2 pt-6",
  homeLink:
    "flex min-h-11 items-center justify-between gap-3 rounded-[14px] px-3.5 text-[14.5px] text-spot-nav hover:bg-white/[.34] hover:text-spot-night [&>svg]:size-[17px]",
  account:
    "flex items-center gap-2 rounded-[18px] border border-white/85 bg-white/[.62] py-2.5 pl-2.5 pr-1 coarse:flex-wrap",
  accountLink: "flex min-w-0 flex-1 items-center gap-2 coarse:basis-[145px]",
  accountText: "flex min-w-0 flex-col items-start",
  accountName:
    "block max-w-full truncate text-[15px] font-medium text-spot-ink coarse:py-[13px]",
  avatar:
    "grid size-9 shrink-0 place-items-center rounded-xl bg-avatar-lilac font-ui text-[12.5px] font-normal leading-none text-[#3f2f6e]",
  iconButton: `${iconButton} size-9`,
  signOutButton: `${iconButton} h-10 w-9`,
  dialog:
    "m-0 h-dvh max-h-dvh w-[min(296px,calc(100vw_-_32px))] max-w-none border-r border-white bg-spot-drawer p-0 text-spot-ink backdrop:bg-[#23273566] backdrop:backdrop-blur-[5px]",
  mobileSidebar: "flex min-h-full flex-col px-4 pb-[18px] pt-6",
  workspace: "block min-w-0",
  topbar:
    "hidden min-h-[68px] items-center justify-between gap-4 border-b border-white/70 bg-white/[.34] px-6 py-3 max-[960px]:flex",
  topbarStart: "flex min-w-0 items-center gap-3 max-[600px]:gap-[7px]",
  menuButton: `${iconButton} size-10`,
  breadcrumb:
    "flex items-center gap-3.5 text-[13px] text-spot-muted max-[760px]:hidden [&>strong]:font-medium [&>strong]:text-spot-ink [&>svg]:size-3 [&>svg]:text-spot-faint",
  mobileBrand:
    "hidden font-display text-[16px] font-normal leading-[1.2] tracking-[-0.025em] max-[760px]:block coarse:min-h-11",
  // Pages scroll normally, with the overview's spacing around them.
  content,
  // Fill pages (documents, clients) stretch their table to 28px above the
  // bottom of wide windows.
  contentFill: `${content} min-[1360px]:flex min-[1360px]:flex-col min-[1360px]:pb-7`,
  // The overview fills one desktop screen; the minimum heights add up the
  // smallest panel sizes in the default and compact layouts.
  contentFit:
    "flex h-dvh min-h-[888px] w-full px-8 pb-7 pt-[30px] outline-none has-[.has-error]:h-auto compact:min-h-[763px] compact:px-6 compact:py-5 max-[1359px]:h-auto max-[1359px]:min-h-dvh max-[1359px]:p-7 max-[960px]:min-h-[calc(100dvh_-_68px)] max-[960px]:p-6 max-[700px]:px-4 max-[700px]:pb-6 max-[700px]:pt-5",
};

const DashboardShell = ({
  children,
  section = "Overview",
  fit = false,
  fill = false,
}) => {
  const { user, googleToken } = useSelector((state) => state.auth);
  const { isAdmin } = useAuthUser();
  const business = useGetBusinessesQuery().data?.businesses?.find(
    (candidate) => candidate.active,
  );
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [logoutUser, { isLoading: signingOut }] = useLogoutUserMutation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const dialogRef = useRef(null);
  const menuButtonRef = useRef(null);
  const desktopWidth = 961;
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

  // ⌘K on a Mac, Ctrl+K elsewhere, opens search unless a dialog is open.
  useEffect(() => {
    const onShortcut = (event) => {
      if (
        !(event.metaKey || event.ctrlKey) ||
        event.altKey ||
        event.shiftKey ||
        event.key.toLowerCase() !== "k"
      )
        return;
      event.preventDefault();
      if (document.querySelector("dialog[open]:not(#command-palette)")) return;
      setSearching((open) => !open);
    };
    window.addEventListener("keydown", onShortcut);
    return () => window.removeEventListener("keydown", onShortcut);
  }, []);
  const shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? "⌘K" : "Ctrl K";
  const openSearch = () => {
    setMenuOpen(false);
    setSearching(true);
  };

  const signOut = async () => {
    setMenuOpen(false);
    await logoutUser();
    navigate("/login", { replace: true });
  };

  const navigation = [
    { to: "/dashboard", label: "Overview", icon: FiGrid },
    { to: "/documents", label: "Documents", icon: FiFileText },
    { to: "/customers", label: "Clients", icon: FiUsers },
    { to: "/items", label: "Items", icon: FiPackage },
    { to: "/reports", label: "Reports", icon: FiBarChart2 },
    { to: "/profile", label: "Profile", icon: FiUser },
    { to: "/settings", label: "Settings", icon: FiSettings },
    ...(isAdmin
      ? [{ to: "/users", label: "Administration", icon: FiShield }]
      : []),
  ];

  const sidebar = (mobile = false) => (
    <>
      <div className={ui.brandRow}>
        <Link to="/" className={ui.brand}>
          Invoice Spot<span>.</span>
        </Link>
        {mobile ? (
          <button
            className={ui.iconButton}
            onClick={() => setMenuOpen(false)}
            aria-label="Close navigation"
          >
            <FiX />
          </button>
        ) : (
          <NotificationBell />
        )}
      </div>
      <button
        type="button"
        className={ui.search}
        onClick={openSearch}
        aria-keyshortcuts="Meta+K Control+K"
      >
        <FiSearch aria-hidden="true" />
        <span>Search</span>
        <kbd className={ui.shortcut} aria-hidden="true">
          {shortcut}
        </kbd>
      </button>
      <nav
        className={ui.navigation}
        aria-label={
          mobile ? "Mobile workspace navigation" : "Workspace navigation"
        }
      >
        {navigation.map(({ to, label, icon: Icon }) => (
          <Link
            key={to}
            to={to}
            aria-current={section === label ? "page" : undefined}
            onClick={() => setMenuOpen(false)}
            className={section === label ? ui.navLinkActive : ui.navLink}
          >
            <Icon aria-hidden="true" />
            <span>{label}</span>
          </Link>
        ))}
      </nav>
      <div className={ui.sidebarBottom}>
        <Link className={ui.homeLink} to="/" title="Back to homepage">
          Back to homepage <FiArrowUpRight aria-hidden="true" />
        </Link>
        <div className={ui.account}>
          <div className={ui.accountLink}>
            <Link
              to="/profile"
              className={ui.avatar}
              tabIndex={-1}
              aria-hidden="true"
            >
              {initials}
            </Link>
            <span className={ui.accountText}>
              <Link
                to="/profile"
                className={ui.accountName}
                aria-label={`Profile for ${name}`}
                title={name}
              >
                {name}
              </Link>
              <BusinessSwitcher
                label={
                  business
                    ? business.role === "OWNER"
                      ? business.name
                      : `${business.name} · ${ROLE_LABELS[business.role]}`
                    : isAdmin
                      ? "Administrator"
                      : "Personal workspace"
                }
              />
            </span>
          </div>
          <button
            className={ui.signOutButton}
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
    <div className={ui.app}>
      <a className={ui.skipLink} href="#dashboard-content">
        Skip to {section.toLowerCase()}
      </a>
      <aside className={ui.sidebar}>{sidebar()}</aside>
      <dialog
        className={ui.dialog}
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
        <div className={ui.mobileSidebar}>{sidebar(true)}</div>
      </dialog>
      <div className={ui.workspace}>
        <header className={ui.topbar}>
          <div className={ui.topbarStart}>
            <button
              className={ui.menuButton}
              ref={menuButtonRef}
              onClick={() => setMenuOpen(true)}
              aria-label="Open navigation"
              aria-expanded={menuOpen}
              aria-haspopup="dialog"
              aria-controls="dashboard-navigation-dialog"
            >
              <FiMenu aria-hidden="true" />
            </button>
            <span className={ui.breadcrumb}>
              Workspace <FiChevronRight aria-hidden="true" />
              <strong>{section}</strong>
            </span>
            <Link to="/" className={ui.mobileBrand}>
              Invoice Spot
            </Link>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              className={ui.menuButton}
              onClick={openSearch}
              aria-label="Search"
              aria-keyshortcuts="Meta+K Control+K"
            >
              <FiSearch aria-hidden="true" />
            </button>
            <NotificationBell className="size-10" />
          </div>
        </header>
        <main
          className={fit ? ui.contentFit : fill ? ui.contentFill : ui.content}
          id="dashboard-content"
          tabIndex={-1}
        >
          {children}
        </main>
      </div>
      <CommandPalette
        open={searching}
        onClose={() => setSearching(false)}
        isAdmin={isAdmin}
      />
    </div>
  );
};

export default DashboardShell;
