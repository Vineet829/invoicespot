package com.invoicespot.document.dto;

import java.util.List;

public record PublicParty(String name, String logo, List<String> lines) {}
