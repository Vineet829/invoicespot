package com.invoicespot.document;

import com.invoicespot.document.dto.PublicDocumentResponse;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/share/{token}")
public class PublicShareController {

    private final ShareService shareService;
    private final RateLimiter rateLimiter;

    public PublicShareController(ShareService shareService, RateLimiter rateLimiter) {
        this.shareService = shareService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public PublicDocumentResponse view(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.SHARED_DOCUMENT, http.getRemoteAddr());
        return shareService.view(token);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.SHARED_DOCUMENT, http.getRemoteAddr());
        return PdfController.inline(shareService.pdf(token));
    }

    @PostMapping("/accept")
    public PublicDocumentResponse accept(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.SHARED_DOCUMENT, http.getRemoteAddr());
        return shareService.accept(token);
    }
}
