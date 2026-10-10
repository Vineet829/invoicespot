package com.invoicespot.document;

// An email ready to look over: who it goes to, its subject and message, and a
// note on what sending it will do, when there is something to know.
public record EmailDraft(String to, String subject, String message, String note) {}
