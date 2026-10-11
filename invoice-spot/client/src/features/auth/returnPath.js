// Where signing in leads back to: the page that asked for it, with its query
// and fragment, such as a list with its filters.
export const returnPath = (from) => {
  if (!from?.pathname) return null;
  return `${from.pathname}${from.search ?? ""}${from.hash ?? ""}`;
};
