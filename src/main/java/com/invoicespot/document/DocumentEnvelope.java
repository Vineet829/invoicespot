package com.invoicespot.document;

import com.invoicespot.settings.EmailTemplateKind;
import java.util.Map;
import java.util.UUID;

// What an email about a document starts from: its kind of email, the
// document's title, who it goes to, and what its placeholders are filled
// with, apart from the client's and the business's names and the link, which
// is the document's own. A statement belongs to no one document.
public record DocumentEnvelope(
        EmailTemplateKind kind,
        String title,
        String recipientName,
        String recipientEmail,
        Map<String, String> values,
        UUID documentId) {}
