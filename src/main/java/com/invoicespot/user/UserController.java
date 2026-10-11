package com.invoicespot.user;

import com.invoicespot.auth.dto.ConfirmIdentityRequest;
import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.user.dto.AdminRoleRequest;
import com.invoicespot.user.dto.AdminStatsResponse;
import com.invoicespot.user.dto.ProfileResponse;
import com.invoicespot.user.dto.UpdateProfileRequest;
import com.invoicespot.user.dto.UpdatedProfileResponse;
import com.invoicespot.user.dto.UserPageResponse;
import com.invoicespot.user.dto.UserProfileResponse;
import com.invoicespot.user.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal User principal) {
        return new ProfileResponse(true, userService.getProfile(principal.getPkid()));
    }

    @PatchMapping("/profile")
    public UpdatedProfileResponse updateProfile(
            @AuthenticationPrincipal User principal, @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse updated = userService.updateProfile(principal.getPkid(), request);
        return new UpdatedProfileResponse(
                true, updated.firstName() + ", your profile was successfully updated", updated);
    }

    @DeleteMapping("/profile")
    public MessageResponse deleteOwnAccount(
            @AuthenticationPrincipal User principal,
            @Valid @RequestBody(required = false) ConfirmIdentityRequest request) {
        userService.deleteOwnAccount(principal.getPkid(), request);
        return new MessageResponse(true, "Your user account has been deleted");
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('Admin')")
    public UserPageResponse listUsers(
            @RequestParam(name = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query) {
        return userService.listUsers(pageNumber, size, query);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('Admin')")
    public AdminStatsResponse stats() {
        return userService.stats();
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('Admin')")
    public UserSummaryResponse reactivateUser(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return userService.reactivateUser(id, principal);
    }

    @PatchMapping("/{id}/admin")
    @PreAuthorize("hasAuthority('Admin')")
    public UserSummaryResponse setAdmin(
            @AuthenticationPrincipal User principal,
            @PathVariable String id,
            @Valid @RequestBody AdminRoleRequest request) {
        return userService.setAdmin(id, principal, request.admin());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Admin')")
    public MessageResponse deleteUser(@AuthenticationPrincipal User principal, @PathVariable String id) {
        String firstName = userService.deleteUser(id, principal);
        return new MessageResponse(true, "User " + firstName + " deleted successfully");
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('Admin')")
    public UserSummaryResponse deactivateUser(@AuthenticationPrincipal User principal, @PathVariable String id) {
        return userService.deactivateUser(id, principal);
    }
}
