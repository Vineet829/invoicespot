// The Items list's search, as kept in the URL, so a link can open the list
// at an item. The catalog comes in one answer, so the search runs here
// rather than on the server.
export const readItemList = (params) => ({ q: params.get("q") || "" });

export const itemListSearch = ({ q }) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  return params;
};

// The items whose name, or unit, holds what was typed, in any case.
export const matchingItems = (items, q) => {
  const typed = q.trim().toLowerCase();
  if (!typed) return items;
  return items.filter((item) =>
    [item.name, item.unit].some(
      (field) =>
        typeof field === "string" && field.toLowerCase().includes(typed),
    ),
  );
};
