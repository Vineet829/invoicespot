package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// The GST helper files for some months: what each holds, the GSTR-3B figures,
// and what was left out: documents made outside GST mode, ones in another
// currency without a rate to rupees, and how many lines have no HSN/SAC code.
public record GstReturnsResponse(
        boolean success,
        YearMonth from,
        YearMonth to,
        BigDecimal b2clLimit,
        List<GstFile> files,
        List<Gstr3bRow> gstr3b,
        long documents,
        long skipped,
        long unconverted,
        long missingHsn) {}
