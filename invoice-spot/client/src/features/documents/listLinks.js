// Links to the Documents, Payments and Expenses lists with filters set, in
// the order the lists write their own addresses, leaving out what isn't set.
// A figure opens the records behind it through these, in its currency when
// the business has more than one.
const listLink = (path, filters) => {
  const search = new URLSearchParams(
    Object.entries(filters).filter(([, value]) => value),
  );
  return `${path}${search.toString() ? `?${search}` : ""}`;
};

export const documentsLink = (filters) => listLink("/documents", filters);

// Invoices with money owed, soonest due first; `due` keeps those due in one
// of the overview's weeks.
export const outstandingLink = (currency, due) =>
  documentsLink({
    status: "outstanding",
    due,
    currency,
    sort: "dueDate",
    dir: "asc",
  });

export const overdueLink = (currency) =>
  documentsLink({ status: "overdue", currency });

export const paymentsLink = (period, currency) =>
  listLink("/payments", { period, currency });

export const expensesLink = (filters) => listLink("/expenses", filters);

// Whether a list is the very one a link opens: the same page, with the same
// filters, in any order and on any page. `read` reads that page's address.
const ORDER_AND_PAGE = ["sort", "dir", "page", "size"];
export const opensList = (link, path, read, list) => {
  const [linkPath, search] = link.split("?");
  if (linkPath !== path) return false;
  const opened = read(new URLSearchParams(search));
  return Object.keys(opened).every(
    (key) => ORDER_AND_PAGE.includes(key) || opened[key] === list[key],
  );
};
