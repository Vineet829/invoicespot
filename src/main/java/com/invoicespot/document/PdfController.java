package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.document.dto.EmailDraftResponse;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document")
public class PdfController {

    private final DocumentService documentService;
    private final DocumentMailService documentMailService;

    public PdfController(DocumentService documentService, DocumentMailService documentMailService) {
        this.documentService = documentService;
        this.documentMailService = documentMailService;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User principal, @PathVariable String id) {
        DocumentPdf pdf = documentService.renderPdf(principal, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    @GetMapping("/{id}/email")
    public EmailDraftResponse emailDraft(@AuthenticationPrincipal User principal, @PathVariable String id) {
        EmailDraft draft = documentMailService.draft(principal, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message());
    }

    @PostMapping("/{id}/email")
    public MessageResponse email(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.email(principal, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Document sent successfully.");
    }
}
