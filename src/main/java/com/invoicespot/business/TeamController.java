package com.invoicespot.business;

import com.invoicespot.auth.dto.ConfirmIdentityRequest;
import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.dto.ChangeRoleRequest;
import com.invoicespot.business.dto.MemberListResponse;
import com.invoicespot.business.dto.MemberResponse;
import com.invoicespot.business.dto.TransferRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The team of the business the session works in.
@RestController
@RequestMapping("/api/v1/business")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/members")
    public MemberListResponse members(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new MemberListResponse(true, teamService.members(access));
    }

    @PatchMapping("/members/{userId}")
    public MemberResponse changeRole(
            @Requires(Permission.MANAGE_TEAM) BusinessAccess access,
            @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return teamService.changeRole(access, userId, request.role());
    }

    @DeleteMapping("/members/{userId}")
    public MessageResponse remove(
            @Requires(Permission.MANAGE_TEAM) BusinessAccess access, @PathVariable String userId) {
        teamService.remove(access, userId);
        return new MessageResponse(true, "They are no longer part of the business");
    }

    @PostMapping("/leave")
    public MessageResponse leave(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        teamService.leave(access);
        return new MessageResponse(true, "You left the business");
    }

    @PostMapping("/transfer")
    public MemberResponse transfer(
            @Requires(Permission.OWN_BUSINESS) BusinessAccess access, @Valid @RequestBody TransferRequest request) {
        return teamService.transfer(access, request.userId());
    }

    @DeleteMapping
    public MessageResponse delete(
            @Requires(Permission.OWN_BUSINESS) BusinessAccess access,
            @Valid @RequestBody(required = false) ConfirmIdentityRequest request) {
        teamService.delete(access, request);
        return new MessageResponse(true, "The business was deleted");
    }
}
