package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import com.invoicespot.report.dto.ReceivablesResponse;
import com.invoicespot.report.dto.SalesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The money bar over the documents list, and the documents' views of what is
// owed and what was sold, beside the documents' own addresses. A path without
// variables is matched before /{id}, so none of these is ever read as a
// document's id.
@RestController
@RequestMapping("/api/v1/document")
public class DocumentSummaryController {

    private final Overview overview;
    private final Receivables receivables;
    private final Sales sales;

    public DocumentSummaryController(Overview overview, Receivables receivables, Sales sales) {
        this.overview = overview;
        this.receivables = receivables;
        this.sales = sales;
    }

    // What is owed now and what came in over the last 30 days, in one
    // currency, as the overview counts them.
    @GetMapping("/summary")
    public DocumentSummaryResponse summary(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "currency", required = false) String currency) {
        return overview.documentSummary(access, currency);
    }

    // What is owed now in one currency, not due yet and overdue, as the
    // overview's total receivables counts it.
    @GetMapping("/receivables")
    public ReceivablesResponse receivables(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "currency", required = false) String currency) {
        return receivables.summary(access, currency);
    }

    // What was billed in one currency over one of the documents list's
    // periods, or all dates when none is given, as the overview's Billed
    // counts it, by kind of document and by tax rate.
    @GetMapping("/sales")
    public SalesResponse sales(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency) {
        return sales.summary(access, period, currency);
    }
}
