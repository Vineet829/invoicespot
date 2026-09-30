package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.ActivityResponse;
import com.invoicespot.document.dto.CreateDocumentRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.CreatedDocumentResponse;
import com.invoicespot.document.dto.DocumentListResponse;
import com.invoicespot.document.dto.DocumentResponse;
import com.invoicespot.document.dto.PaymentResultResponse;
import com.invoicespot.document.dto.SingleDocumentResponse;
import com.invoicespot.document.dto.UpdateDocumentRequest;
import com.invoicespot.document.dto.UpdatePaymentRequest;
import com.invoicespot.document.dto.UpdatedDocumentResponse;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
            @AuthenticationPrincipal User principal, @Valid @RequestBody CreateDocumentRequest request) {
        return new CreatedDocumentResponse(true, documentService.create(principal.getPkid(), request));
    }

    @GetMapping("/all")
    public DocumentListResponse listMine(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "overdue", required = false) Boolean overdue,
            @RequestParam(name = "customerId", required = false) String customerId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction) {
        return documentService.listMine(
                principal.getPkid(),
                DocumentSearch.of(query, status, type, overdue, customerId, from, to, sort, direction, page, size));
    }

    @PostMapping("/{id}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResultResponse addPayment(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody CreatePaymentRequest request) {
        return new PaymentResultResponse(
                true,
                "Payment has been recorded successfully",
                documentService.addPayment(principal.getPkid(), id, request));
    }

    @PatchMapping("/{id}/payment/{paymentId}")
    public PaymentResultResponse updatePayment(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @PathVariable String paymentId,
            @Valid @RequestBody UpdatePaymentRequest request) {
        return new PaymentResultResponse(
                true,
                "Payment has been updated",
                documentService.updatePayment(principal.getPkid(), id, paymentId, request));
    }

    @DeleteMapping("/{id}/payment/{paymentId}")
    public PaymentResultResponse deletePayment(
            @AuthenticationPrincipal User principal, @PathVariable String id, @PathVariable String paymentId) {
        return new PaymentResultResponse(
                true, "Payment has been deleted", documentService.deletePayment(principal.getPkid(), id, paymentId));
    }

    @GetMapping("/{id}")
    public SingleDocumentResponse getOne(
            @AuthenticationPrincipal User principal, @PathVariable String id) {
        return new SingleDocumentResponse(true, documentService.getOne(principal.getPkid(), id));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDocumentResponse duplicate(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return new CreatedDocumentResponse(true, documentService.duplicate(principal.getPkid(), id));
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDocumentResponse convert(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return new CreatedDocumentResponse(true, documentService.convert(principal.getPkid(), id));
    }

    @GetMapping("/{id}/activity")
    public ActivityResponse activity(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return documentService.activity(principal.getPkid(), id);
    }

    @PatchMapping("/{id}")
    public UpdatedDocumentResponse update(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateDocumentRequest request) {
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
