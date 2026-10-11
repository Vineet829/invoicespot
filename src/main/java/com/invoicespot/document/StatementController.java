package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.document.dto.EmailDraftResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/{id}/statement")
public class StatementController {

    private final StatementService statementService;
    private final DocumentMailService documentMailService;

    public StatementController(StatementService statementService, DocumentMailService documentMailService) {
        this.statementService = statementService;
        this.documentMailService = documentMailService;
    }

    @GetMapping
    public ResponseEntity<byte[]> pdf(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return PdfController.inline(statementService.render(access, id));
    }

    // A draft is for whoever can send it, and holds the client's portal link.
    @GetMapping("/email")
    public EmailDraftResponse emailDraft(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        EmailDraft draft = documentMailService.statementDraft(access, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message(), draft.note());
    }

    @PostMapping("/email")
    public MessageResponse email(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.emailStatement(access, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Statement sent successfully.");
    }
}
