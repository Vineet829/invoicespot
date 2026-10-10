package com.invoicespot.document.dto;

import java.time.LocalDate;
import java.util.List;

// One page of the documents a search matches; the days they were dated
// between when the search named any, as the dates or the period asked for;
// and what all of them come to in each currency, by currency code. How many
// there are is what the totals count.
public record DocumentListResponse(
        boolean success,
        long totalDocuments,
        int numberOfPages,
        int page,
        int size,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        List<DocumentResponse> myDocuments,
        List<DocumentListTotals> totals) {}
