// How many of something, in words: "1 invoice", "1,200 invoices".
export const countOf = (count, one, many) =>
  `${Number(count || 0).toLocaleString("en")} ${count === 1 ? one : many}`;
export const invoiceCount = (count) => countOf(count, "invoice", "invoices");
