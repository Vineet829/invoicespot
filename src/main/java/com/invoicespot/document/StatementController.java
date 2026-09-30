package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.document.dto.EmailDraftResponse;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return PdfController.inline(statementService.render(principal, id));
    }

    @GetMapping("/email")
    public EmailDraftResponse emailDraft(@AuthenticationPrincipal User principal, @PathVariable String id) {
        EmailDraft draft = documentMailService.statementDraft(principal, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message());
    }

    @PostMapping("/email")
    public MessageResponse email(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.emailStatement(principal, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Statement sent successfully.");
    }
}
