package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The money bar over the documents list, beside the documents' own addresses.
@RestController
@RequestMapping("/api/v1/document")
public class DocumentSummaryController {

    private final Overview overview;

    public DocumentSummaryController(Overview overview) {
        this.overview = overview;
    }

    // What is owed now and what came in over the last 30 days, in one
    // currency, as the overview counts them. A path without variables is
    // matched before /{id}, so this one is never read as a document's id.
    @GetMapping("/summary")
    public DocumentSummaryResponse summary(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "currency", required = false) String currency) {
        return overview.documentSummary(access, currency);
    }
}
