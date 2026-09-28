import { expect, it } from "vitest";
import {
  customerSnapshot,
  documentPayload,
  documentTotals,
  lineAmount,
} from "./documentEditorModel";

const customer = {
  _id: "client-1",
  name: "Morrow & Co.",
  email: "morrow@example.com",
  accountNo: "CUS-001",
  vatTinNo: 100200300,
  address: "18 Mason Street",
  city: "San Francisco",
  country: "United States",
  phoneNumber: "+15550100001",
};
const input = {
  customer,
  documentType: "Invoice",
  dueDate: "2026-10-01T06:30:00.000Z",
  rates: "10",
  currency: "USD",
  additionalInfo: "Project work",
  termsConditions: "Due in 14 days",
  items: [
    { itemName: "Design", quantity: "2", unitPrice: "150", discount: "10" },
  ],
};

it("calculates discounted lines and tax from the same rounded line amounts shown in the editor", () => {
  expect(
    lineAmount({ quantity: "3", unitPrice: "19.99", discount: "12.5" }),
  ).toBe(52.47);
  expect(documentTotals(input.items, input.rates)).toEqual({
    subTotal: 270,
    salesTax: 27,
    total: 297,
  });
  expect(documentTotals([], "")).toEqual({
    subTotal: 0,
    salesTax: 0,
    total: 0,
  });
});

it("serializes only the existing backend document fields with their DTO types", () => {
  expect(documentPayload(input)).toEqual({
    customer: {
      name: "Morrow & Co.",
      email: "morrow@example.com",
      accountNo: "CUS-001",
      vatTinNo: "100200300",
      address: "18 Mason Street",
      city: "San Francisco",
      country: "United States",
      phoneNumber: "+15550100001",
    },
    documentType: "Invoice",
    dueDate: input.dueDate,
    additionalInfo: "Project work",
    termsConditions: "Due in 14 days",
    status: "Not Paid",
    subTotal: 270,
    salesTax: 27,
    total: 297,
    rates: "10",
    currency: "USD",
    totalAmountReceived: 0,
    billingItems: [
      { itemName: "Design", quantity: 2, unitPrice: 150, discount: "10" },
    ],
  });
});

it("keeps the existing document type, payment status and amount received when editing", () => {
  const result = documentPayload({
    ...input,
    documentType: "Quotation",
    doc: { status: "Not Fully Paid", totalAmountReceived: 120 },
  });
  expect(result).toMatchObject({
    documentType: "Quotation",
    status: "Not Fully Paid",
    totalAmountReceived: 120,
    dueDate: input.dueDate,
  });
  expect(documentPayload({ ...input, documentType: "Receipt" }).status).toBe(
    "Paid",
  );
});

it("supports optional customer details without sending nulls or database identifiers", () => {
  expect(customerSnapshot({ name: "Avery", vatTinNo: 0 })).toEqual({
    name: "Avery",
    email: "",
    accountNo: "",
    vatTinNo: "0",
    address: "",
    city: "",
    country: "",
    phoneNumber: "",
  });
});
