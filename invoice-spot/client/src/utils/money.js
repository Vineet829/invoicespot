// Amounts as people read them in their currency: rupees grouped in lakhs and
// crores (1,00,000.00), every other currency in thousands (100,000.00), as
// the PDFs print them.
const localeOf = (currency) =>
  String(currency || "").toUpperCase() === "INR" ? "en-IN" : "en";

// Building a number format costs far more than using one, and charts format
// many amounts at a time, so each locale and set of options gets one.
const formats = new Map();
const numberFormat = (locale, options) => {
  const key = `${locale} ${JSON.stringify(options)}`;
  if (!formats.has(key)) {
    formats.set(key, new Intl.NumberFormat(locale, options));
  }
  return formats.get(key);
};

export const formatAmount = (value, currency) =>
  numberFormat(localeOf(currency), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);

// A short form for chart axes: 1.2L and 1.2Cr for rupees, 1.2K and 1.2M
// otherwise.
export const compactAmount = (value, currency) =>
  numberFormat(localeOf(currency), {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value) || 0);
