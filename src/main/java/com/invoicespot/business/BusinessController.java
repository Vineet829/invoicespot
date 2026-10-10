package com.invoicespot.business;

import com.invoicespot.business.dto.BusinessEnvelope;
import com.invoicespot.business.dto.UpdateBusinessRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The details of the business the session is working in.
@RestController
@RequestMapping("/api/v1/business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    public BusinessEnvelope get(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new BusinessEnvelope(true, null, businessService.profile(access));
    }

    @PatchMapping
    public BusinessEnvelope update(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @Valid @RequestBody UpdateBusinessRequest request) {
        return new BusinessEnvelope(
                true, "Your business details were saved", businessService.updateProfile(access, request));
    }
}
