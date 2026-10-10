import { expect, it } from "vitest";
import { taxRows } from "./gstRows";

const taxes = [
  { rate: 0, tax: 0, cgst: 0, sgst: 0, igst: 0 },
  { rate: 5, tax: 5.06, cgst: 2.53, sgst: 2.53, igst: 0 },
];

it("shows one row per rate for standard tax", () => {
  expect(taxRows(taxes, undefined, "VAT")).toEqual([
    ["VAT (0%)", 0],
    ["VAT (5%)", 5.06],
  ]);
});

it("halves the rate into CGST and SGST within the state, or UTGST in a union territory", () => {
  expect(taxRows(taxes, { supplyType: "INTRA" }, "GST")).toEqual([
    ["CGST 2.5%", 2.53],
    ["SGST 2.5%", 2.53],
  ]);
  expect(
    taxRows(taxes, { supplyType: "INTRA", unionTerritory: true }, "GST")[1][0],
  ).toBe("UTGST 2.5%");
});

it("shows IGST at the full rate otherwise, marked when the recipient pays", () => {
  expect(
    taxRows(
      [{ rate: 18, tax: 180, cgst: 0, sgst: 0, igst: 180 }],
      { supplyType: "INTER", reverseCharge: true },
      "GST",
    ),
  ).toEqual([["IGST 18% (recipient pays)", 180]]);
});
