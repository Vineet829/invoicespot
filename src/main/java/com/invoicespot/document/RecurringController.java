package com.invoicespot.document;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.document.dto.RecurringEnvelope;
import com.invoicespot.document.dto.RecurringListResponse;
import com.invoicespot.document.dto.RecurringRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public RecurringListResponse list(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return recurringService.list(access);
    }

    @PostMapping("/api/v1/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringEnvelope create(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @Valid @RequestBody RecurringRequest request) {
        return new RecurringEnvelope(
                true, "The invoice now repeats", recurringService.create(access, request));
    }

    @GetMapping("/api/v1/document/{id}/recurring")
    public RecurringEnvelope forDocument(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new RecurringEnvelope(
                true, null, recurringService.forDocument(access, id).orElse(null));
    }

    @PatchMapping("/api/v1/recurring/{id}")
    public RecurringEnvelope update(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody RecurringRequest request) {
        return new RecurringEnvelope(
                true, "The schedule was saved", recurringService.update(access, id, request));
    }

    @PostMapping("/api/v1/recurring/{id}/pause")
    public RecurringEnvelope pause(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new RecurringEnvelope(
                true, "The schedule is paused", recurringService.setPaused(access, id, true));
    }

    @PostMapping("/api/v1/recurring/{id}/resume")
    public RecurringEnvelope resume(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return new RecurringEnvelope(
                true, "The schedule is running again", recurringService.setPaused(access, id, false));
    }

    @DeleteMapping("/api/v1/recurring/{id}")
    public MessageResponse delete(@Requires(Permission.DELETE_RECORDS) BusinessAccess access, @PathVariable String id) {
        recurringService.delete(access, id);
        return new MessageResponse(true, "The invoice no longer repeats");
    }
}
