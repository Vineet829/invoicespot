package com.invoicespot.settings.dto;

import com.invoicespot.settings.EmailTemplate;
import com.invoicespot.settings.EmailTemplateKind;
import java.util.List;

// One kind of email: what it says now, whether the business wrote it, the
// standard text it goes back to, and the placeholders it can use.
public record EmailTemplateResponse(
        EmailTemplateKind kind,
        String label,
        String subject,
        String body,
        boolean custom,
        String standardSubject,
        String standardBody,
        List<EmailTemplateKind.Placeholder> placeholders) {

    public static EmailTemplateResponse of(EmailTemplateKind kind, EmailTemplate template) {
        return new EmailTemplateResponse(
                kind,
                kind.label(),
                template == null ? kind.standardSubject() : template.getSubject(),
                template == null ? kind.standardBody() : template.getBody(),
                template != null,
                kind.standardSubject(),
                kind.standardBody(),
                kind.placeholders());
    }
}
