package com.invoicespot.report.dto;

import java.math.BigDecimal;

// One line of the GSTR-3B outward supplies, in rupees, under its table (none
// for a line the return has no table for). A tax that doesn't apply to it is
// null.
public record Gstr3bRow(
        String table, String description, BigDecimal taxable, BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {}
