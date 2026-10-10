package com.invoicespot.auth.session;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.auth.session.dto.SessionListResponse;
import com.invoicespot.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public SessionListResponse list(@AuthenticationPrincipal User principal) {
        return new SessionListResponse(true, sessionService.list(principal, CurrentSession.id().orElse(null)));
    }

    @DeleteMapping("/{id}")
    public MessageResponse signOut(@AuthenticationPrincipal User principal, @PathVariable String id) {
        sessionService.signOut(principal, id, CurrentSession.id().orElse(null));
        return new MessageResponse(true, "That device was signed out");
    }

    @PostMapping("/sign-out-others")
    public MessageResponse signOutOthers(@AuthenticationPrincipal User principal) {
        int ended = sessionService.signOutOthers(principal, CurrentSession.id().orElse(null));
        return new MessageResponse(
                true,
                ended == 0
                        ? "No other device was signed in"
                        : ended == 1 ? "1 other device was signed out" : ended + " other devices were signed out");
    }
}
