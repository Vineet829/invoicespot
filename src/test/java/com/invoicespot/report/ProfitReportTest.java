package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfitReportTest {

    private record Row(String category, BigDecimal cost) {}

    private static Row row(String category, String cost) {
        return new Row(category, new BigDecimal(cost));
    }

    @Test
    @DisplayName("categories go by cost, then by name whatever its case, then by spelling, in whatever order they come")
    void largestFirst() {
        List<Row> rows = List.of(row("travel", "50"), row("Office", "70"), row("Travel", "50.00"), row("ads", "50"));

        for (List<Row> given : List.of(rows, rows.reversed())) {
            assertThat(given.stream().sorted(ProfitReport.largestFirst(Row::cost, Row::category)).map(Row::category))
                    .containsExactly("Office", "ads", "Travel", "travel");
        }
    }
}
