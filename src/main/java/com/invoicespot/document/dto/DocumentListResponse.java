package com.invoicespot.document.dto;

import java.util.List;

public record DocumentListResponse(
        boolean success, long totalDocuments, int numberOfPages, List<DocumentResponse> myDocuments) {}
