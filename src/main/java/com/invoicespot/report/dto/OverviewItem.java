package com.invoicespot.report.dto;

import java.math.BigDecimal;

// An item the overview's period sold: how many, what its lines came to before
// tax, and on how many documents; a credit note's lines take theirs off. An
// item is its name in any letter case, spelled as it was first used, and lines
// without a name are one item, "Other lines".
public record OverviewItem(String item, BigDecimal quantity, BigDecimal amount, long documents) {}
