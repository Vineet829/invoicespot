package com.invoicespot.document;

import com.invoicespot.document.dto.DeclineRequest;
import com.invoicespot.document.dto.PortalResponse;
import com.invoicespot.document.dto.PublicDocumentResponse;
import com.invoicespot.document.dto.ReportPaymentRequest;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// A client's portal, opened by its link with no sign-in. Documents are named
// by their number, in the query, since a number may hold a slash.
@RestController
@RequestMapping("/api/v1/public/portal/{token}")
public class PublicPortalController {

    private final PortalService portalService;
    private final RateLimiter rateLimiter;

    public PublicPortalController(PortalService portalService, RateLimiter rateLimiter) {
        this.portalService = portalService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public PortalResponse view(
            @PathVariable String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return portalService.view(token, page, size);
    }

    @GetMapping("/document")
    public PublicDocumentResponse document(
            @PathVariable String token, @RequestParam String number, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return portalService.document(token, number);
    }

    @GetMapping("/document/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable String token, @RequestParam String number, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return PdfController.inline(portalService.pdf(token, number));
    }

    @PostMapping("/document/accept")
    public PublicDocumentResponse accept(
            @PathVariable String token, @RequestParam String number, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return portalService.accept(token, number);
    }

    @PostMapping("/document/decline")
    public PublicDocumentResponse decline(
            @PathVariable String token,
            @RequestParam String number,
            @Valid @RequestBody(required = false) DeclineRequest request,
            HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return portalService.decline(token, number, request == null ? null : request.reason());
    }

    @PostMapping("/document/payment-claims")
    public PublicDocumentResponse reportPayment(
            @PathVariable String token,
            @RequestParam String number,
            @Valid @RequestBody ReportPaymentRequest request,
            HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        rateLimiter.consume(RateLimit.PAYMENT_CLAIM, http.getRemoteAddr());
        return portalService.reportPayment(token, number, request);
    }

    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.CLIENT_PORTAL, http.getRemoteAddr());
        return PdfController.inline(portalService.statement(token));
    }
}
