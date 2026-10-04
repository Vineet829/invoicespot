package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.report.dto.PaymentListResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentList paymentList;

    public PaymentController(PaymentList paymentList) {
        this.paymentList = paymentList;
    }

    // A method is a payment method's name, or receipt for receipts; a period
    // is the last 30 or 90 days or 12 months, as the overview has them.
    @GetMapping
    public PaymentListResponse list(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(name = "sort", defaultValue = "date") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return paymentList.list(
                access, PaymentSearch.of(query, method, currency, period, from, to, sort, direction, page, size));
    }
}
