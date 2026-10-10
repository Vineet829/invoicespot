package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.document.dto.ConfirmClaimRequest;
import com.invoicespot.document.dto.DismissClaimRequest;
import com.invoicespot.document.dto.PaymentClaimEnvelope;
import com.invoicespot.document.dto.PaymentClaimsResponse;
import com.invoicespot.document.dto.WaitingClaimsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentClaimController {

    private static final String ON_A_DOCUMENT = "/api/v1/document/{id}/payment-claims";

    private final PaymentClaimService paymentClaimService;

    public PaymentClaimController(PaymentClaimService paymentClaimService) {
        this.paymentClaimService = paymentClaimService;
    }

    // Reports waiting anywhere in the business.
    @GetMapping("/api/v1/payment-claims")
    public WaitingClaimsResponse waiting(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new WaitingClaimsResponse(true, paymentClaimService.waiting(access));
    }

    @GetMapping(ON_A_DOCUMENT)
    public PaymentClaimsResponse list(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new PaymentClaimsResponse(true, paymentClaimService.forDocument(access, id));
    }

    @PostMapping(ON_A_DOCUMENT + "/{claimId}/confirm")
    public PaymentClaimEnvelope confirm(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String claimId,
            @Valid @RequestBody ConfirmClaimRequest request) {
        return new PaymentClaimEnvelope(
                true, "The payment was recorded", paymentClaimService.confirm(access, id, claimId, request));
    }

    @PostMapping(ON_A_DOCUMENT + "/{claimId}/dismiss")
    public PaymentClaimEnvelope dismiss(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String claimId,
            @Valid @RequestBody(required = false) DismissClaimRequest request) {
        return new PaymentClaimEnvelope(
                true, "The payment report was dismissed", paymentClaimService.dismiss(access, id, claimId, request));
    }
}
