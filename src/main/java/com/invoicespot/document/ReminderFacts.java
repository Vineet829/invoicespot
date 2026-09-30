package com.invoicespot.document;

public record ReminderFacts(
        String title, String clientName, String clientEmail, String balance, String due, boolean overdue) {}
