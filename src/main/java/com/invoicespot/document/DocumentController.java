package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
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
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final DocumentExport documentExport;

    public DocumentController(DocumentService documentService, DocumentExport documentExport) {
        this.documentService = documentService;
        this.documentExport = documentExport;
    }

    @PostMapping("/create")
    public CreatedDocumentResponse create(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @Valid @RequestBody CreateDocumentRequest request) {
        return new CreatedDocumentResponse(true, documentService.create(access, request));
    }

    @GetMapping("/all")
    public DocumentListResponse listMine(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
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
                access,
                DocumentSearch.of(query, status, type, overdue, customerId, from, to, sort, direction, page, size));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @Requires(Permission.EXPORT_LISTS) BusinessAccess access,
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
        return documentExport
                .documents(
                        access,
                        DocumentSearch.of(query, status, type, overdue, customerId, from, to, sort, direction, 1, 1))
                .download();
    }

    @GetMapping("/payments/csv")
    public ResponseEntity<byte[]> paymentsCsv(@Requires(Permission.EXPORT_LISTS) BusinessAccess access) {
        return documentExport.payments(access).download();
    }

    @PostMapping("/{id}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResultResponse addPayment(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody CreatePaymentRequest request) {
        return new PaymentResultResponse(
                true,
                "Payment has been recorded successfully",
                documentService.addPayment(access, id, request));
    }

    @PatchMapping("/{id}/payment/{paymentId}")
    public PaymentResultResponse updatePayment(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String paymentId,
            @Valid @RequestBody UpdatePaymentRequest request) {
        return new PaymentResultResponse(
                true,
                "Payment has been updated",
                documentService.updatePayment(access, id, paymentId, request));
    }

    @DeleteMapping("/{id}/payment/{paymentId}")
    public PaymentResultResponse deletePayment(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @PathVariable String paymentId) {
        return new PaymentResultResponse(
                true, "Payment has been deleted", documentService.deletePayment(access, id, paymentId));
    }

    @GetMapping("/{id}")
    public SingleDocumentResponse getOne(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new SingleDocumentResponse(true, documentService.getOne(access, id));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDocumentResponse duplicate(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new CreatedDocumentResponse(true, documentService.duplicate(access, id));
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDocumentResponse convert(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new CreatedDocumentResponse(true, documentService.convert(access, id));
    }

    @GetMapping("/{id}/activity")
    public ActivityResponse activity(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return documentService.activity(access, id);
    }

    @PatchMapping("/{id}")
    public UpdatedDocumentResponse update(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        DocumentResponse updated = documentService.update(access, id, request);
        return new UpdatedDocumentResponse(
                true,
                "Your " + updated.documentType().getValue() + "'s info was updated successfully",
                updated);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(
            @Requires(Permission.DELETE_RECORDS) BusinessAccess access, @PathVariable String id) {
        documentService.delete(access, id);
        return new MessageResponse(true, "Your document has been deleted");
    }
}
