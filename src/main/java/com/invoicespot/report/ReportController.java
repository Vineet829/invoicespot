package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.report.dto.GstReturnsResponse;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.ReportResponse;
import com.invoicespot.report.dto.TaxReportResponse;
import com.invoicespot.report.dto.TdsReportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final ReportService reportService;
    private final TdsReport tdsReport;
    private final TaxReport taxReport;
    private final GstReturns gstReturns;
    private final Overview overview;

    public ReportController(
            ReportService reportService,
            TdsReport tdsReport,
            TaxReport taxReport,
            GstReturns gstReturns,
            Overview overview) {
        this.reportService = reportService;
        this.tdsReport = tdsReport;
        this.taxReport = taxReport;
        this.gstReturns = gstReturns;
        this.overview = overview;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @RequestParam(required = false) String currency) {
        return overview.summary(access, currency);
    }

    @GetMapping
    public ReportResponse report(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String view) {
        return reportService.report(access, period, view);
    }

    @GetMapping("/tds")
    public TdsReportResponse tds(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @RequestParam(required = false) String period) {
        return tdsReport.report(access, period);
    }

    @GetMapping("/tax")
    public TaxReportResponse tax(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String view) {
        return taxReport.report(access, period, view);
    }

    // Months as YYYY-MM, last month when none are given.
    @GetMapping("/gst")
    public GstReturnsResponse gst(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return gstReturns.summary(access, from, to);
    }

    @GetMapping("/gst/{section}/csv")
    public ResponseEntity<byte[]> gstCsv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
            @PathVariable String section,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return gstReturns.csv(access, section, from, to).download();
    }

    @GetMapping("/csv/{section}")
    public ResponseEntity<byte[]> csv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
            @PathVariable String section,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String view) {
        return (switch (section) {
                    case "tds" -> tdsReport.csv(access, period);
                    case "tax" -> taxReport.csv(access, period, view);
                    default -> reportService.csv(access, period, view, section);
                })
                .download();
    }
}
