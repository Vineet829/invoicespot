package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.document.dto.EmailPreviewResponse;
import com.invoicespot.settings.dto.EmailTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Previews an email being written in Settings on the business's own
// documents, which is why it lives with them. Only those who can change the
// emails preview them.
@RestController
public class EmailPreviewController {

    private final DocumentMailService documentMailService;

    public EmailPreviewController(DocumentMailService documentMailService) {
        this.documentMailService = documentMailService;
    }

    @PostMapping("/api/v1/settings/email-templates/{kind}/preview")
    public EmailPreviewResponse preview(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @PathVariable String kind,
            @Valid @RequestBody EmailTemplateRequest request) {
        return documentMailService.preview(access, kind, request);
    }
}
