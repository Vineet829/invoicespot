package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.report.dto.PaymentListResponse;
import com.invoicespot.report.dto.PaymentTotals;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The payments list: money in and out of the business's issued documents, a
// page at a time, with what all of it comes to. Over one of the overview's
// periods it adds up to the overview's Received.
@Service
public class PaymentList {

    private final PaymentQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public PaymentList(PaymentQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // A period ends today in the business's time zone. The page and the
    // totals are read at one moment, so the two agree; the totals count the
    // rows.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentListResponse list(BusinessAccess access, PaymentSearch search) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        PaymentSearch dated = search.on(LocalDate.now(clock.withZone(zone)));
        List<PaymentTotals> totals = queries.totals(access.businessPkid(), dated);
        long found = totals.stream().mapToLong(PaymentTotals::payments).sum();
        return new PaymentListResponse(
                true,
                found,
                (int) ((found + search.size() - 1) / search.size()),
                search.page(),
                search.size(),
                dated.from(),
                dated.to(),
                queries.page(access.businessPkid(), dated),
                totals,
                queries.methods(access.businessPkid()));
    }
}
