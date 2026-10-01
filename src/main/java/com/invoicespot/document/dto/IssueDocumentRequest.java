package com.invoicespot.document.dto;

import java.time.Instant;
import java.time.LocalDate;

// All are optional: a draft is dated the day planned for it, else today, and
// keeps its due date and exchange rate. A blank exchange rate removes it.
public record IssueDocumentRequest(LocalDate issueDate, Instant dueDate, String exchangeRate) {}
