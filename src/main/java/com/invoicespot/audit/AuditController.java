package com.invoicespot.audit;

import com.invoicespot.audit.dto.ActivityEventResponse;
import com.invoicespot.audit.dto.AuditEventResponse;
import com.invoicespot.audit.dto.EventPageResponse;
import com.invoicespot.audit.dto.TeamActivityResponse;
import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.business.Requires;
import com.invoicespot.user.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditActivity auditActivity;

    public AuditController(AuditActivity auditActivity) {
        this.auditActivity = auditActivity;
    }

    @GetMapping("/api/v1/account/activity")
    public EventPageResponse<ActivityEventResponse> mine(
            @AuthenticationPrincipal User principal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return auditActivity.mine(principal, CurrentSession.id().orElse(null), page, size);
    }

    @GetMapping("/api/v1/business/activity")
    public EventPageResponse<TeamActivityResponse> team(
            @Requires(Permission.VIEW_TEAM_ACTIVITY) BusinessAccess access,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return auditActivity.team(access.businessPkid(), page, size);
    }

    @GetMapping("/api/v1/admin/audit")
    @PreAuthorize("hasAuthority('Admin')")
    public EventPageResponse<AuditEventResponse> all(
            @RequestParam(name = "q", required = false) String email,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return auditActivity.all(email, type, page, size);
    }
}
