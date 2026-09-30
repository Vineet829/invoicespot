import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  FiAlertCircle,
  FiBell,
  FiCheckCircle,
  FiEye,
  FiRepeat,
} from "react-icons/fi";
import { toast } from "react-toastify";
import usePageVisible from "../../hooks/usePageVisible";
import { Dialog } from "../../components/workspace/Dialog";
import {
  iconButton,
  panelNote,
  secondaryButton,
} from "../../components/workspace/pastel";
import {
  useGetNotificationsQuery,
  useMarkNotificationsReadMutation,
} from "./notificationsApiSlice";
import { timeAgo } from "./timeAgo";

const ICONS = {
  DOCUMENT_VIEWED: FiEye,
  QUOTATION_ACCEPTED: FiCheckCircle,
  INVOICE_OVERDUE: FiAlertCircle,
  RECURRING_CREATED: FiRepeat,
  REMINDER_SENT: FiBell,
};

// The bell checks for news every minute while the tab is in view, and at
// once when it comes back. The sidebar shows no counts, so unread news is a
// dot; the number is for screen readers and the list itself.
const NotificationBell = ({ className = "" }) => {
  const visible = usePageVisible();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const query = useGetNotificationsQuery(undefined, {
    pollingInterval: visible ? 60000 : 0,
  });
  const [markRead, { isLoading: marking }] = useMarkNotificationsReadMutation();
  const { refetch } = query;
  const wasVisible = useRef(visible);
  useEffect(() => {
    if (visible && !wasVisible.current) refetch();
    wasVisible.current = visible;
  }, [visible, refetch]);
  const unread = query.data?.unread ?? 0;
  const notifications = query.data?.notifications || [];
  const openNotification = async (notification) => {
    setOpen(false);
    if (!notification.read) markRead(notification._id);
    if (notification.documentId)
      navigate(`/document/${notification.documentId}`);
  };
  const markAll = async () => {
    try {
      await markRead().unwrap();
    } catch {
      toast.error("The notifications couldn’t be marked as read.");
    }
  };
  return (
    <>
      <button
        type="button"
        className={`${iconButton} relative size-9 ${className}`}
        onClick={() => setOpen(true)}
        aria-label={
          unread ? `Notifications, ${unread} unread` : "Notifications"
        }
        aria-haspopup="dialog"
      >
        <FiBell aria-hidden="true" />
        {unread > 0 && (
          <span
            className="absolute right-[7px] top-[7px] size-2 rounded-full bg-spot-coral ring-2 ring-white"
            aria-hidden="true"
          />
        )}
      </button>
      <Dialog
        open={open}
        title="Notifications"
        onClose={() => setOpen(false)}
        size="medium"
        closeLabel="Close notifications"
      >
        {query.isError ? (
          <p className={`mt-4 ${panelNote}`}>
            Notifications couldn’t be loaded. They will be checked again in a
            minute.
          </p>
        ) : notifications.length ? (
          <>
            <ul className="-mx-3 mt-4 flex list-none flex-col p-0">
              {notifications.map((notification) => {
                const Icon = ICONS[notification.type] || FiBell;
                return (
                  <li key={notification._id}>
                    <button
                      type="button"
                      className="flex w-full items-start gap-3 rounded-2xl bg-transparent px-3 py-3 text-left transition-colors hover:bg-spot-violet/[.06]"
                      onClick={() => openNotification(notification)}
                    >
                      <span
                        className={`grid size-9 shrink-0 place-items-center rounded-xl [&>svg]:size-4 [&>svg]:stroke-[1.7] ${notification.type === "INVOICE_OVERDUE" ? "bg-overdue text-overdue-strong" : "bg-spot-violet/[.07] text-spot-violet"}`}
                        aria-hidden="true"
                      >
                        <Icon />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span
                          className={`block text-[14px] leading-[1.45] [overflow-wrap:anywhere] ${notification.read ? "text-spot-body" : "font-medium text-spot-ink"}`}
                        >
                          {notification.message}
                        </span>
                        <span className="mt-0.5 block text-[12.5px] text-spot-muted">
                          <time dateTime={notification.createdAt}>
                            {timeAgo(notification.createdAt)}
                          </time>
                          {!notification.read && (
                            <span className="sr-only">, unread</span>
                          )}
                        </span>
                      </span>
                      {!notification.read && (
                        <span
                          className="mt-2 size-2 shrink-0 rounded-full bg-spot-violet"
                          aria-hidden="true"
                        />
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
            {unread > 0 && (
              <div className="mt-4 flex justify-end">
                <button
                  type="button"
                  className={secondaryButton}
                  onClick={markAll}
                  disabled={marking}
                >
                  Mark all as read
                </button>
              </div>
            )}
          </>
        ) : (
          <p className={`mt-4 ${panelNote}`}>
            Nothing new. You’ll hear here when a client views or accepts a
            document, and when an invoice becomes overdue.
          </p>
        )}
      </Dialog>
    </>
  );
};

export default NotificationBell;
