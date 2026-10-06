package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.report.dto.CustomerSalesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sales by customer, beside the clients' own addresses. A path without
// variables is matched before /{id}, so it is never read as a client's id.
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerSalesController {

    private final CustomerSales customerSales;

    public CustomerSalesController(CustomerSales customerSales) {
        this.customerSales = customerSales;
    }

    // Every client billed in one currency over one of the documents list's
    // periods, or all dates when none is given, sorted by billed, paid, owed
    // or name, the most billed first unless another is asked for.
    @GetMapping("/sales")
    public CustomerSalesResponse sales(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "sort", defaultValue = "billed") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return customerSales.list(access, period, currency, sort, direction, page, size);
    }
}
