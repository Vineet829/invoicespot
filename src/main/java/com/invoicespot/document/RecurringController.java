package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.document.dto.RecurringEnvelope;
import com.invoicespot.document.dto.RecurringListResponse;
import com.invoicespot.document.dto.RecurringRequest;
import com.invoicespot.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping("/api/v1/recurring")
    public RecurringListResponse list(@AuthenticationPrincipal User principal) {
        return recurringService.list(principal.getPkid());
    }

    @PostMapping("/api/v1/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringEnvelope create(
            @AuthenticationPrincipal User principal, @Valid @RequestBody RecurringRequest request) {
        return new RecurringEnvelope(
                true, "The invoice now repeats", recurringService.create(principal.getPkid(), request));
    }

    @GetMapping("/api/v1/document/{id}/recurring")
    public RecurringEnvelope forDocument(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return new RecurringEnvelope(
                true, null, recurringService.forDocument(principal.getPkid(), id).orElse(null));
    }

    @PatchMapping("/api/v1/recurring/{id}")
    public RecurringEnvelope update(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody RecurringRequest request) {
        return new RecurringEnvelope(
                true, "The schedule was saved", recurringService.update(principal.getPkid(), id, request));
    }

    @PostMapping("/api/v1/recurring/{id}/pause")
    public RecurringEnvelope pause(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return new RecurringEnvelope(
                true, "The schedule is paused", recurringService.setPaused(principal.getPkid(), id, true));
    }

    @PostMapping("/api/v1/recurring/{id}/resume")
    public RecurringEnvelope resume(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return new RecurringEnvelope(
                true, "The schedule is running again", recurringService.setPaused(principal.getPkid(), id, false));
    }

    @DeleteMapping("/api/v1/recurring/{id}")
    public MessageResponse delete(@AuthenticationPrincipal User principal, @PathVariable String id) {
        recurringService.delete(principal.getPkid(), id);
        return new MessageResponse(true, "The invoice no longer repeats");
    }
}
