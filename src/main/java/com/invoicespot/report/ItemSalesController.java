package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.report.dto.ItemSalesDetailResponse;
import com.invoicespot.report.dto.ItemSalesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sales by item, beside the items' own addresses.
@RestController
@RequestMapping("/api/v1/item")
public class ItemSalesController {

    private final ItemSales itemSales;

    public ItemSalesController(ItemSales itemSales) {
        this.itemSales = itemSales;
    }

    // Every item sold in one currency over one of the documents list's
    // periods, or all dates when none is given, the largest first.
    @GetMapping("/sales")
    public ItemSalesResponse sales(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return itemSales.list(access, period, currency, page, size);
    }

    // One item's sales over the same days, asked for by the name the list
    // gives it.
    @GetMapping("/sales/detail")
    public ItemSalesDetailResponse detail(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency) {
        return itemSales.detail(access, name, period, currency);
    }
}
