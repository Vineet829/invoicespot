// How long ago something happened, in the words a notification uses.
export const timeAgo = (value, now = new Date()) => {
  const then = new Date(value);
  const minutes = Math.floor((now - then) / 60000);
  if (!Number.isFinite(minutes)) return "";
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return days === 1 ? "Yesterday" : `${days} days ago`;
  return then.toLocaleDateString("en", {
    day: "numeric",
    month: "short",
    ...(then.getFullYear() !== now.getFullYear() && { year: "numeric" }),
  });
};
