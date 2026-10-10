package com.invoicespot.business;

import com.invoicespot.business.dto.InvitationPreviewResponse;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// An invitation link, opened before anyone signs in.
@RestController
@RequestMapping("/api/v1/public/invitations")
public class PublicInvitationController {

    private final InvitationService invitationService;
    private final RateLimiter rateLimiter;

    public PublicInvitationController(InvitationService invitationService, RateLimiter rateLimiter) {
        this.invitationService = invitationService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{token}")
    public InvitationPreviewResponse preview(@PathVariable String token, HttpServletRequest request) {
        rateLimiter.consume(RateLimit.INVITATION_LOOKUP, request.getRemoteAddr());
        return invitationService.preview(token);
    }
}
