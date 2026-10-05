package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

// A client the overview's period billed: what it was billed, by how many
// documents, and what it owes now. The id is that of the client the documents
// are linked to; documents linked to none are one client by the name they were
// saved with, and have no id.
public record OverviewClient(UUID clientId, String client, BigDecimal billed, BigDecimal owed, long documents) {}
