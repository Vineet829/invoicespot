package com.invoicespot.document.dto;

import java.time.Instant;
import java.time.LocalDate;

// Both are optional: a draft is dated the day planned for it, else today,
// and keeps its due date.
public record IssueDocumentRequest(LocalDate issueDate, Instant dueDate) {}
