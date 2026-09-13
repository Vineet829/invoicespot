package com.invoicespot.document;

import com.invoicespot.document.dto.GeneratePdfRequest;
import com.invoicespot.document.dto.PdfMessage;
import com.invoicespot.document.dto.PdfProfileDto;
import com.invoicespot.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document")
public class PdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfController.class);

    private final PdfService pdfService;
    private final MailService mailService;

    public PdfController(PdfService pdfService, MailService mailService) {
        this.pdfService = pdfService;
        this.mailService = mailService;
    }

    @PostMapping("/generate-pdf")
    public ResponseEntity<PdfMessage> generate(@RequestBody GeneratePdfRequest request) {
        try {
            pdfService.generateAndSave(request);
            return ResponseEntity.ok(new PdfMessage("PDF generated successfully."));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to generate PDF", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PdfMessage("Failed to generate PDF."));
        }
    }

    @GetMapping("/get-pdf")
    public ResponseEntity<?> get() {
        return pdfService.readLatest()
                .<ResponseEntity<?>>map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"myDocument.pdf\"")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new PdfMessage("No document found. Generate one first.")));
    }

    @PostMapping("/send-pdf")
    public ResponseEntity<PdfMessage> send(@RequestBody GeneratePdfRequest request) {
        try {
            byte[] pdf = pdfService.generateAndSave(request);
            String recipient = request.document() != null && request.document().customer() != null
                    ? request.document().customer().email()
                    : null;
            String replyTo = request.profile() != null ? request.profile().email() : null;
            String sender = senderName(request.profile());
            mailService.sendDocument(
                    recipient,
                    replyTo,
                    "Document from " + sender,
                    "Here is your document from " + sender,
                    "myDocument.pdf",
                    pdf);
            return ResponseEntity.ok(new PdfMessage("Document sent successfully."));
        } catch (Exception exception) {
            LOGGER.error("Failed to send the document", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PdfMessage("Failed to send the document."));
        }
    }

    private static String senderName(PdfProfileDto profile) {
        if (profile == null) {
            return "Invoice Spot";
        }
        if (profile.businessName() != null && !profile.businessName().isBlank()) {
            return profile.businessName();
        }
        return profile.firstName() != null ? profile.firstName() : "Invoice Spot";
    }
}
