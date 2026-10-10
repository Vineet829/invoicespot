package com.invoicespot.settings;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.settings.dto.PaymentDetailsEnvelope;
import com.invoicespot.settings.dto.PaymentDetailsRequest;
import com.invoicespot.settings.dto.SettingsEnvelope;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsEnvelope get(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new SettingsEnvelope(true, null, settingsService.get(access));
    }

    @PutMapping
    public SettingsEnvelope update(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @Valid @RequestBody UpdateSettingsRequest request) {
        return new SettingsEnvelope(true, "Your settings were saved", settingsService.update(access, request));
    }

    @GetMapping("/payment-details")
    public PaymentDetailsEnvelope paymentDetails(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new PaymentDetailsEnvelope(true, null, settingsService.paymentDetails(access));
    }

    @PutMapping("/payment-details")
    public PaymentDetailsEnvelope updatePaymentDetails(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @Valid @RequestBody PaymentDetailsRequest request) {
        return new PaymentDetailsEnvelope(
                true, "Your payment details were saved", settingsService.updatePaymentDetails(access, request));
    }
}
