// Amounts as people read them in their currency: rupees grouped in lakhs and
// crores (1,00,000.00), every other currency in thousands (100,000.00), as
// the PDFs print them.
const localeOf = (currency) =>
  String(currency || "").toUpperCase() === "INR" ? "en-IN" : "en";

export const formatAmount = (value, currency) =>
  new Intl.NumberFormat(localeOf(currency), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);

// A short form for chart axes: 1.2L and 1.2Cr for rupees, 1.2K and 1.2M
// otherwise.
export const compactAmount = (value, currency) =>
  new Intl.NumberFormat(localeOf(currency), {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value) || 0);
