// A document's tax as rows of a summary. Standard tax is one row per rate;
// India's GST shows CGST and SGST (UTGST in a union territory without a
// legislature) at half the rate within the state, or IGST at the full rate,
// and says when the recipient pays it under reverse charge.
export const taxRows = (taxes, gst, taxLabel) => {
  if (!gst?.supplyType) {
    return taxes.map((tax) => [`${taxLabel} (${tax.rate}%)`, tax.tax]);
  }
  const payer = gst.reverseCharge ? " (recipient pays)" : "";
  return taxes
    .filter((tax) => Number(tax.rate) > 0)
    .flatMap((tax) => {
      if (gst.supplyType === "INTRA") {
        const half = `${Number(tax.rate) / 2}%`;
        return [
          [`CGST ${half}${payer}`, tax.cgst],
          [
            `${gst.unionTerritory ? "UTGST" : "SGST"} ${half}${payer}`,
            tax.sgst,
          ],
        ];
      }
      return [[`IGST ${tax.rate}%${payer}`, tax.igst]];
    });
};
