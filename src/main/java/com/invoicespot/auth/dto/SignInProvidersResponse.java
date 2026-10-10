package com.invoicespot.auth.dto;

import java.util.List;

public record SignInProvidersResponse(boolean success, List<Provider> providers) {

    public record Provider(String id, String name) {}
}
