package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.ReportResponse;
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
    private final Overview overview;

    public ReportController(ReportService reportService, Overview overview) {
        this.reportService = reportService;
        this.overview = overview;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @RequestParam(required = false) String currency) {
        return overview.summary(access, currency);
    }

    @GetMapping
    public ReportResponse report(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @RequestParam(required = false) String period) {
        return reportService.report(access, period);
    }

    @GetMapping("/csv/{section}")
    public ResponseEntity<byte[]> csv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
            @PathVariable String section,
            @RequestParam(required = false) String period) {
        return reportService.csv(access, period, section).download();
    }
}
