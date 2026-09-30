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
export const documentTotals = (items, rates) => {
  const subTotal = roundMoney(
    items.reduce((sum, item) => sum + lineAmount(item), 0),
  );
  const salesTax = roundMoney((subTotal * (Number(rates) || 0)) / 100);
  return { subTotal, salesTax, total: roundMoney(subTotal + salesTax) };
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
  rates,
  currency,
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
  rates: String(rates || "0"),
  currency,
  billingItems: items.map((item) => ({
    itemName: item.itemName || "",
    quantity: Number(item.quantity) || 0,
    unitPrice: Number(item.unitPrice) || 0,
    discount: String(item.discount || "0"),
  })),
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
