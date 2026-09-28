export const lineAmount = (item) =>
  Number(
    (
      (Number(item.quantity) || 0) *
      (Number(item.unitPrice) || 0) *
      (1 - (Number(item.discount) || 0) / 100)
    ).toFixed(2),
  );
export const documentTotals = (items, rates) => {
  const subTotal = items.reduce((sum, item) => sum + lineAmount(item), 0);
  const salesTax = ((Number(rates) || 0) / 100) * subTotal;
  return { subTotal, salesTax, total: subTotal + salesTax };
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
export const documentPayload = ({
  doc,
  customer,
  documentType,
  dueDate,
  rates,
  currency,
  items,
  additionalInfo,
  termsConditions,
}) => ({
  customer: customerSnapshot(customer),
  documentType,
  dueDate,
  additionalInfo,
  termsConditions,
  status: documentType === "Receipt" ? "Paid" : doc?.status || "Not Paid",
  ...documentTotals(items, rates),
  rates: String(rates || "0"),
  currency,
  totalAmountReceived: doc?.totalAmountReceived ?? 0,
  billingItems: items.map((item) => ({
    itemName: item.itemName || "",
    quantity: Number(item.quantity) || 0,
    unitPrice: Number(item.unitPrice) || 0,
    discount: String(item.discount || "0"),
  })),
});
