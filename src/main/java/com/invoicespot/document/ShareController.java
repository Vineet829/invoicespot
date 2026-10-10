package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.document.dto.ShareLinkResponse;
import com.invoicespot.document.dto.ShareStatusResponse;
import com.invoicespot.document.dto.WhatsAppShareResponse;
import org.springframework.http.HttpStatus;
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
    public ShareLinkResponse create(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return shareService.create(access, id);
    }

    @GetMapping
    public ShareStatusResponse status(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return shareService.status(access, id);
    }

    @PostMapping("/whatsapp")
    public WhatsAppShareResponse whatsApp(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return shareService.whatsApp(access, id);
    }

    @DeleteMapping
    public MessageResponse revoke(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        shareService.revoke(access, id);
        return new MessageResponse(true, "The share link was turned off.");
    }
}
