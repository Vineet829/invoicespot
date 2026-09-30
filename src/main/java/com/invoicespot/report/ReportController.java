package com.invoicespot.report;

import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.ReportResponse;
import com.invoicespot.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal User principal, @RequestParam(required = false) String currency) {
        return overview.summary(principal.getPkid(), currency);
    }

    @GetMapping
    public ReportResponse report(
            @AuthenticationPrincipal User principal, @RequestParam(required = false) String period) {
        return reportService.report(principal.getPkid(), period);
    }

    @GetMapping("/csv/{section}")
    public ResponseEntity<byte[]> csv(
            @AuthenticationPrincipal User principal,
            @PathVariable String section,
            @RequestParam(required = false) String period) {
        return reportService.csv(principal.getPkid(), period, section).download();
    }
}
