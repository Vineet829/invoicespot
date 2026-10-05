package com.invoicespot.report.dto;

import java.util.List;

// What is left to set up in a business, step by step: its details, its logo,
// how clients pay it, its first invoice and its team, each done or not.
public record OverviewSetup(List<Step> steps) {

    public record Step(String key, boolean done) {}
}
