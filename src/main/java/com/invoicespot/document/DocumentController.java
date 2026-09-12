package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.CreatedDocumentResponse;
import com.invoicespot.document.dto.DocumentListResponse;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.SingleDocumentResponse;
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.document.dto.UpdatedDocumentResponse;
import com.invoicespot.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/create")
    public CreatedDocumentResponse create(
            @AuthenticationPrincipal User principal, @RequestBody CreateDocumentRequest request) {
        return new CreatedDocumentResponse(true, documentService.create(principal.getPkid(), request));
    }

    @GetMapping("/all")
    public DocumentListResponse listMine(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "page", defaultValue = "1") int page) {
        return documentService.listMine(principal.getPkid(), page);
    }

    @PostMapping("/{id}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse addPayment(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @RequestBody CreatePaymentRequest request) {
        documentService.addPayment(principal.getPkid(), id, request);
        return new MessageResponse(true, "Payment has been recorded successfully");
    }

    @GetMapping("/{id}")
    public SingleDocumentResponse getOne(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        return new SingleDocumentResponse(true, documentService.getOne(principal.getPkid(), id));
    }

    @PatchMapping("/{id}")
    public UpdatedDocumentResponse update(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @RequestBody UpdateDocumentRequest request) {
        DocumentResponse updated = documentService.update(principal.getPkid(), id, request);
        return new UpdatedDocumentResponse(
                true,
                "Your " + updated.documentType().getValue() + "'s info was updated successfully",
                updated);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        documentService.delete(principal.getPkid(), id);
        return new MessageResponse(true, "Your document has been deleted");
    }
}
