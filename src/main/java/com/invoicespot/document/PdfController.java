package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.document.dto.EmailDraftResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<byte[]> pdf(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return inline(documentService.renderPdf(access, id));
    }

    static ResponseEntity<byte[]> inline(DocumentPdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    @GetMapping("/{id}/payment/{paymentId}/receipt")
    public ResponseEntity<byte[]> receipt(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String paymentId) {
        return inline(documentService.renderReceipt(access, id, paymentId));
    }

    // A draft is for whoever can send it, and holds the document's link.
    @GetMapping("/{id}/payment/{paymentId}/receipt/email")
    public EmailDraftResponse receiptDraft(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String paymentId) {
        EmailDraft draft = documentMailService.receiptDraft(access, id, paymentId);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message(), draft.note());
    }

    @PostMapping("/{id}/payment/{paymentId}/receipt/email")
    public MessageResponse emailReceipt(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String paymentId,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.emailReceipt(
                access, id, paymentId, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Receipt sent successfully.");
    }

    @GetMapping("/{id}/remind")
    public EmailDraftResponse reminderDraft(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        EmailDraft draft = documentMailService.reminderDraft(access, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message(), draft.note());
    }

    @PostMapping("/{id}/remind")
    public MessageResponse remind(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.remind(access, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Reminder sent successfully.");
    }

    @GetMapping("/{id}/email")
    public EmailDraftResponse emailDraft(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        EmailDraft draft = documentMailService.draft(access, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message(), draft.note());
    }

    @PostMapping("/{id}/email")
    public MessageResponse email(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.email(access, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "Document sent successfully.");
    }
}
