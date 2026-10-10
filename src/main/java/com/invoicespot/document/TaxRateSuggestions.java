package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.Percentage;
import com.invoicespot.document.dto.TaxRateSuggestionsResponse;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The tax rates and units a business actually uses, to offer when a line is
// typed: from its items, its lines of the last year and its default rate.
// No list of rates is built in, since they change.
@Service
public class TaxRateSuggestions {

    private static final Duration LOOK_BACK = Duration.ofDays(365);
    private static final int MOST_UNITS = 20;

    private final JdbcClient jdbc;
    private final SettingsService settingsService;
    private final Clock clock;

    public TaxRateSuggestions(JdbcClient jdbc, SettingsService settingsService, Clock clock) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // Rates most used first, the default rate always among them; units the
    // same way.
    @Transactional(readOnly = true)
    public TaxRateSuggestionsResponse suggest(BusinessAccess access) {
        Long businessPkid = access.businessPkid();
        Timestamp since = Timestamp.from(Instant.now(clock).minus(LOOK_BACK));
        Map<BigDecimal, Long> rates = new TreeMap<>();
        rates.put(settingsService.forBusiness(businessPkid).getDefaultTaxRate(), 0L);
        jdbc.sql("""
                        select rate, count(*) as uses from (
                            select i.tax_rate as rate from document_billing_items i
                            join documents d on d.pkid = i.document_pkid
                            where d.business_pkid = :business and d.created_at >= :since
                            union all
                            select c.tax_rate from catalog_items c
                            where c.business_pkid = :business and c.tax_rate is not null
                        ) used
                        group by rate
                        """)
                .param("business", businessPkid)
                .param("since", since)
                .query((result, rowNumber) -> Map.entry(result.getBigDecimal("rate"), result.getLong("uses")))
                .list()
                .forEach(used -> rates.merge(used.getKey(), used.getValue(), Long::sum));
        List<TaxRateSuggestionsResponse.Rate> suggested = new ArrayList<>();
        rates.forEach((rate, uses) ->
                suggested.add(new TaxRateSuggestionsResponse.Rate(Percentage.plain(rate), uses)));
        suggested.sort(Comparator.comparingLong(TaxRateSuggestionsResponse.Rate::uses).reversed()
                .thenComparing(TaxRateSuggestionsResponse.Rate::rate));
        List<String> units = jdbc.sql("""
                        select unit from (
                            select i.unit from document_billing_items i
                            join documents d on d.pkid = i.document_pkid
                            where d.business_pkid = :business and d.created_at >= :since and i.unit is not null
                            union all
                            select c.unit from catalog_items c
                            where c.business_pkid = :business and c.unit is not null
                        ) used
                        group by unit
                        order by count(*) desc, lower(unit)
                        limit :most
                        """)
                .param("business", businessPkid)
                .param("since", since)
                .param("most", MOST_UNITS)
                .query(String.class)
                .list();
        return new TaxRateSuggestionsResponse(true, suggested, units);
    }
}
