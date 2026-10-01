import { normalizeHsnSac } from "../../gst/hsnSac";

// Rounds half up to the cent, as the server does. Going through 15
// significant digits first drops binary noise, so 1.005 becomes 1.01.
export const roundMoney = (value) =>
  Number(`${Math.round(Number(`${Number(value).toPrecision(15)}e2`))}e-2`);
// The editor's running totals. The server computes the saved ones with the
// same rules: each line and the tax are rounded to the cent.
export const lineAmount = (item) =>
  roundMoney(
    (Number(item.quantity) || 0) *
      (Number(item.unitPrice) || 0) *
      (1 - (Number(item.discount) || 0) / 100),
  );
// Lines taxed at the same rate are added up first, and each rate's tax is
// rounded to the cent, lowest rate first; 18 and 18.00 are one rate.
export const taxGroups = (items) => {
  const groups = new Map();
  for (const item of items) {
    const rate = Number(item.taxRate) || 0;
    groups.set(rate, roundMoney((groups.get(rate) || 0) + lineAmount(item)));
  }
  return [...groups]
    .sort(([a], [b]) => a - b)
    .map(([rate, taxable]) => ({
      rate,
      taxable,
      tax: roundMoney((taxable * rate) / 100),
    }));
};
// Where a GST supply goes, as the server works it out: the place chosen, else
// the client's GSTIN or state, else the seller's own state.
export const gstPlace = ({ chosen, customer, sellerState }) =>
  chosen ||
  (customer?.gstin ? customer.gstin.slice(0, 2) : customer?.stateCode) ||
  sellerState ||
  "";

// Within the seller's state, to another state, or out of India under the
// LUT or with IGST.
export const gstSupply = ({ place, sellerState, exportType }) =>
  !place
    ? null
    : place === "96"
      ? exportType === "IGST"
        ? "EXPORT_IGST"
        : "EXPORT_LUT"
      : place === sellerState
        ? "INTRA"
        : "INTER";

// The editor's totals under GST, split as the server splits them: each half
// rounded on its own within the state, IGST at the full rate otherwise,
// nothing on an export under the LUT, and nothing billed when the recipient
// pays under reverse charge.
export const gstTotals = (items, supply, reverseCharge) => {
  const lut = supply === "EXPORT_LUT";
  const base = documentTotals(
    lut ? items.map((item) => ({ ...item, taxRate: 0 })) : items,
  );
  const groups = base.groups.map((group) => {
    if (supply === "INTRA") {
      const half = roundMoney((group.taxable * group.rate) / 200);
      return {
        ...group,
        cgst: half,
        sgst: half,
        igst: 0,
        tax: roundMoney(half * 2),
      };
    }
    return { ...group, cgst: 0, sgst: 0, igst: group.tax };
  });
  const tax = roundMoney(groups.reduce((sum, group) => sum + group.tax, 0));
  const salesTax = reverseCharge ? 0 : tax;
  return {
    ...base,
    groups,
    salesTax,
    total: roundMoney(base.subTotal + salesTax),
  };
};
export const documentTotals = (items) => {
  const subTotal = roundMoney(
    items.reduce((sum, item) => sum + lineAmount(item), 0),
  );
  const groups = taxGroups(items);
  const salesTax = roundMoney(
    groups.reduce((sum, group) => sum + group.tax, 0),
  );
  return { subTotal, groups, salesTax, total: roundMoney(subTotal + salesTax) };
};
export const customerSnapshot = (customer) =>
  customer &&
  Object.fromEntries(
    [
      "name",
      "email",
      "accountNo",
      "vatTinNo",
      "address",
      "city",
      "country",
      "phoneNumber",
    ].map((key) => [key, String(customer[key] ?? "")]),
  );
// The issue date is sent only when one is chosen; left empty, the server
// dates the document the day it is issued, in the business's time zone.
export const documentPayload = ({
  customer,
  documentType,
  issueDate,
  dueDate,
  currency,
  exchangeRate,
  items,
  additionalInfo,
  termsConditions,
}) => ({
  customerId: customer?._id || undefined,
  customer: customerSnapshot(customer),
  documentType,
  issueDate: issueDate || undefined,
  dueDate,
  additionalInfo,
  termsConditions,
  currency,
  // Blank removes the rate the draft had.
  exchangeRate: exchangeRate === undefined ? undefined : exchangeRate.trim(),
  billingItems: items.map((item) => ({
    itemName: item.itemName || "",
    quantity: Number(item.quantity) || 0,
    unitPrice: Number(item.unitPrice) || 0,
    discount: String(item.discount || "0"),
    unit: item.unit?.trim() || undefined,
    taxRate: String(item.taxRate || "0"),
    hsnSac: normalizeHsnSac(item.hsnSac) || undefined,
  })),
});

// A credit note sends only what can change on it: its lines, date, notes and
// reason. Its client, currency and exchange rate stay its invoice's.
export const creditNotePayload = (payload, creditReason) => ({
  issueDate: payload.issueDate,
  billingItems: payload.billingItems,
  additionalInfo: payload.additionalInfo,
  termsConditions: payload.termsConditions,
  creditReason: creditReason.trim(),
});

// Clients whose name, email or account number contains the query, by name.
export const matchClients = (clients, query) => {
  const term = query.trim().toLowerCase();
  return clients
    .filter(
      (client) =>
        !term ||
        [client.name, client.email, client.accountNo].some((value) =>
          String(value || "")
            .toLowerCase()
            .includes(term),
        ),
    )
    .sort((a, b) => String(a.name).localeCompare(String(b.name)));
};
