import { useEffect, useId, useRef } from "react";
import { FiX } from "react-icons/fi";
import { iconButton, sectionTitle } from "./pastel";

const WIDTHS = {
  small: "w-[min(460px,calc(100vw_-_32px))]",
  medium: "w-[min(580px,calc(100vw_-_32px))]",
};

// A modal in the browser's top layer, so no panel can paint over it. It
// focuses the first [data-autofocus] element and returns focus on close.
// Esc closes it unless `busy`; a click on the backdrop closes it only when
// `dismissible`, so a stray click never throws away a form's draft.
export const Dialog = ({
  open,
  title,
  children,
  onClose,
  busy = false,
  dismissible = true,
  size = "small",
  closeLabel = "Close dialog",
}) => {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!open) {
      if (dialog.open) dialog.close();
      return undefined;
    }
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    if (!dialog.open) dialog.showModal();
    dialog.querySelector("[data-autofocus]")?.focus();
    document.body.style.overflow = "hidden";
    return () => {
      if (dialog.open) dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);
  return (
    <dialog
      ref={ref}
      className={`m-auto max-h-[calc(100dvh_-_48px)] max-w-none overflow-auto rounded-3xl border border-white bg-white p-0 text-spot-ink shadow-float backdrop:bg-[#23273566] backdrop:backdrop-blur-[5px] ${WIDTHS[size]}`}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (dismissible && event.target === event.currentTarget && !busy)
          onClose();
      }}
    >
      <div className="relative p-7 max-[700px]:p-6">
        <button
          type="button"
          className={`${iconButton} absolute right-3 top-3 size-9`}
          aria-label={closeLabel}
          onClick={onClose}
          disabled={busy}
        >
          <FiX aria-hidden="true" />
        </button>
        <h2 id={titleId} className={`${sectionTitle} pr-10`}>
          {title}
        </h2>
        {children}
      </div>
    </dialog>
  );
};
