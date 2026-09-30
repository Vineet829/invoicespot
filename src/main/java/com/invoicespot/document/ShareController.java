package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.ShareLinkResponse;
import com.invoicespot.document.dto.ShareStatusResponse;
import com.invoicespot.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document/{id}/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse create(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return shareService.create(principal.getPkid(), id);
    }

    @GetMapping
    public ShareStatusResponse status(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return shareService.status(principal.getPkid(), id);
    }

    @DeleteMapping
    public MessageResponse revoke(@AuthenticationPrincipal User principal, @PathVariable String id) {
        shareService.revoke(principal.getPkid(), id);
        return new MessageResponse(true, "The share link was turned off.");
    }
}
