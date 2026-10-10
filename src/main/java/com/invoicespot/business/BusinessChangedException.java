package com.invoicespot.business;

import com.invoicespot.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

// The browser is working in a business it no longer can, and should switch
// to the one named in the header, or to none.
public class BusinessChangedException extends ApiException {

    public static final String HEADER = "X-Active-Business";

    private final String activeBusiness;

    private BusinessChangedException(String message, String activeBusiness) {
        super(HttpStatus.CONFLICT, message);
        this.activeBusiness = activeBusiness;
    }

    public static BusinessChangedException none() {
        return new BusinessChangedException("You aren't part of a business yet. Start one to carry on.", "none");
    }

    public static BusinessChangedException to(UUID businessId) {
        return new BusinessChangedException(
                "You switched business in another tab, so this tab switched too.", businessId.toString());
    }

    public String getActiveBusiness() {
        return activeBusiness;
    }
}
