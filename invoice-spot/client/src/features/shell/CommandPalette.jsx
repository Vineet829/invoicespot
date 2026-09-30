import { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  FiBarChart2,
  FiFileText,
  FiGrid,
  FiPackage,
  FiPlus,
  FiRepeat,
  FiSearch,
  FiSettings,
  FiShield,
  FiUser,
  FiUserPlus,
  FiUsers,
} from "react-icons/fi";
import { useGetAllUserCustomersQuery } from "../customers/customersApiSlice";
import { useGetAllMyDocsQuery } from "../documents/documentsApiSlice";

const PAGES = [
  { to: "/dashboard", label: "Overview", icon: FiGrid },
  { to: "/documents", label: "Documents", icon: FiFileText },
  { to: "/recurring", label: "Recurring invoices", icon: FiRepeat },
  { to: "/customers", label: "Clients", icon: FiUsers },
  { to: "/items", label: "Items", icon: FiPackage },
  { to: "/reports", label: "Reports", icon: FiBarChart2 },
  { to: "/profile", label: "Profile", icon: FiUser },
  { to: "/settings", label: "Settings", icon: FiSettings },
  { to: "/users", label: "Administration", icon: FiShield, admin: true },
];

const ACTIONS = [
  {
    to: "/create-doc",
    label: "Create document",
    hint: "Invoice, quotation or receipt",
    icon: FiPlus,
  },
  { to: "/create-customer", label: "Add client", icon: FiUserPlus },
];

const NO_ROWS = [];

// The shell renders the palette, so it formats amounts itself rather than
// importing the workspace components, which import the shell.
const amount = (value, currency) =>
  `${currency ? `${currency} ` : ""}${new Intl.NumberFormat("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0)}`;

// The typed text, once typing pauses.
const useSettled = (value, delay) => {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
};

const matches = (text, query) =>
  text.toLowerCase().includes(query.toLowerCase());

// Search and go anywhere in the workspace: pages, quick actions, clients
// and documents. A combobox over one list of options, so arrow keys move
// through every group and Enter opens the highlighted one.
const Palette = ({ isAdmin, onClose }) => {
  const navigate = useNavigate();
  const inputRef = useRef(null);
  const listId = useId();
  const [text, setText] = useState("");
  const [active, setActive] = useState(0);
  const query = useSettled(text.trim(), 200);
  const search = { q: query, size: 5 };
  const clients = useGetAllUserCustomersQuery(search, { skip: !query });
  const documents = useGetAllMyDocsQuery(search, { skip: !query });
  const typed = text.trim();

  const groups = [
    {
      label: "Go to",
      options: PAGES.filter(
        (page) => (isAdmin || !page.admin) && matches(page.label, typed),
      ).map((page) => ({ ...page, key: page.to, meta: "Page" })),
    },
    {
      label: "Create",
      options: ACTIONS.filter((action) =>
        matches(`${action.label} ${action.hint || ""}`, typed),
      ).map((action) => ({ ...action, key: action.to, meta: "Action" })),
    },
    {
      label: "Clients",
      options: (query ? clients.data?.myCustomers || NO_ROWS : NO_ROWS).map(
        (client) => ({
          key: `client-${client._id}`,
          to: `/single-customer/${client._id}`,
          label: client.name,
          hint: client.email,
          icon: FiUsers,
          meta: "Client",
        }),
      ),
    },
    {
      label: "Documents",
      options: (query ? documents.data?.myDocuments || NO_ROWS : NO_ROWS).map(
        (document) => ({
          key: `document-${document._id}`,
          to: `/document/${document._id}`,
          label: document.documentNumber,
          hint: [
            document.customer?.name,
            amount(document.total, document.currency),
          ]
            .filter(Boolean)
            .join(" · "),
          icon: FiFileText,
          meta: document.documentType,
        }),
      ),
    },
  ].filter((group) => group.options.length);
  const options = groups.flatMap((group) => group.options);
  const searching =
    Boolean(typed) &&
    (typed !== query || clients.isFetching || documents.isFetching);
  const current = Math.min(active, Math.max(options.length - 1, 0));
  const optionId = (index) => `${listId}-${index}`;

  useEffect(() => {
    document
      .getElementById(optionId(current))
      ?.scrollIntoView({ block: "nearest" });
  });

  const open = (option) => {
    onClose();
    navigate(option.to);
  };
  const move = (step) =>
    options.length &&
    setActive((current + step + options.length) % options.length);
  const onKeyDown = (event) => {
    const keys = {
      ArrowDown: () => move(1),
      ArrowUp: () => move(-1),
      Home: () => setActive(0),
      End: () => setActive(options.length - 1),
      Enter: () => options[current] && open(options[current]),
    };
    if (!keys[event.key]) return;
    event.preventDefault();
    keys[event.key]();
  };

  let index = -1;
  return (
    <div className="flex max-h-[min(560px,calc(100dvh_-_96px))] flex-col">
      <div className="flex items-center gap-3 border-b border-[#23273512] px-5">
        <FiSearch
          aria-hidden="true"
          className="size-[18px] shrink-0 text-spot-muted"
        />
        <input
          ref={inputRef}
          data-autofocus
          className="h-14 min-w-0 flex-1 border-0 bg-transparent text-[16px] text-spot-ink outline-none placeholder:text-spot-faint"
          role="combobox"
          aria-label="Search the workspace"
          aria-expanded={options.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={options.length ? optionId(current) : undefined}
          placeholder="Search clients and documents, or jump to a page"
          autoComplete="off"
          spellCheck={false}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setActive(0);
          }}
          onKeyDown={onKeyDown}
        />
        <kbd className="shrink-0 rounded-md border border-[#23273524] px-1.5 py-0.5 font-ui text-[11px] text-spot-muted max-[700px]:hidden">
          Esc
        </kbd>
      </div>
      <ul
        id={listId}
        role="listbox"
        aria-label="Results"
        aria-busy={searching || undefined}
        className="m-0 min-h-0 flex-1 list-none overflow-y-auto p-2"
      >
        {groups.map((group) => (
          <li key={group.label} role="presentation">
            <p
              className="px-3 pb-1.5 pt-3 text-[11.5px] uppercase tracking-[0.06em] text-spot-muted"
              aria-hidden="true"
            >
              {group.label}
            </p>
            <ul role="group" aria-label={group.label} className="m-0 p-0">
              {group.options.map((option) => {
                index += 1;
                const position = index;
                const Icon = option.icon;
                const selected = position === current;
                return (
                  <li
                    key={option.key}
                    id={optionId(position)}
                    role="option"
                    aria-selected={selected}
                    className={`flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 ${selected ? "bg-spot-violet/[.09]" : ""}`}
                    onMouseMove={() => setActive(position)}
                    onClick={() => open(option)}
                  >
                    <span
                      className={`grid size-8 shrink-0 place-items-center rounded-[10px] [&>svg]:size-4 [&>svg]:stroke-[1.7] ${selected ? "bg-white text-spot-violet" : "bg-spot-violet/[.06] text-spot-nav"}`}
                      aria-hidden="true"
                    >
                      <Icon />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[14px] text-spot-ink">
                        {option.label}
                      </span>
                      {option.hint && (
                        <span className="block truncate text-[12.5px] text-spot-muted">
                          {option.hint}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-[12px] text-spot-faint">
                      {option.meta}
                    </span>
                  </li>
                );
              })}
            </ul>
          </li>
        ))}
      </ul>
      <p
        className="border-t border-[#23273512] px-5 py-3 text-[12.5px] text-spot-muted"
        role="status"
      >
        {searching
          ? "Searching…"
          : options.length
            ? `${options.length} ${options.length === 1 ? "result" : "results"}. Use the arrow keys, then Enter to open.`
            : "Nothing matches. Try a client’s name or a document number."}
      </p>
    </div>
  );
};

// A modal in the browser's top layer, near the top of the window like a
// spotlight. Esc or a click outside closes it, and focus goes back to where
// it was. The content mounts fresh each time, so it opens empty.
const CommandPalette = ({ open, onClose, isAdmin }) => {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!open) {
      if (dialog.open) dialog.close();
      return undefined;
    }
    const previousFocus = document.activeElement;
    if (!dialog.open) dialog.showModal();
    dialog.querySelector("[data-autofocus]")?.focus();
    return () => {
      if (dialog.open) dialog.close();
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);
  return (
    <dialog
      ref={ref}
      id="command-palette"
      aria-label="Search the workspace"
      className="mx-auto mb-auto mt-[12vh] w-[min(640px,calc(100vw_-_32px))] max-w-none overflow-hidden rounded-3xl border border-white bg-white p-0 text-spot-ink shadow-float backdrop:bg-[#23273566] backdrop:backdrop-blur-[5px] max-[700px]:mt-4"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      {open && <Palette isAdmin={isAdmin} onClose={onClose} />}
    </dialog>
  );
};

export default CommandPalette;
