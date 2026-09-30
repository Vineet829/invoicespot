package com.invoicespot.business;

import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import java.util.UUID;
import org.springframework.http.HttpStatus;

// Who is acting, in which business, and in what role, for one request.
public record BusinessAccess(Long businessPkid, UUID businessId, BusinessRole role, User user) {

    public Long userPkid() {
        return user.getPkid();
    }

    public boolean can(Permission permission) {
        return role.allows(permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, permission.refusal());
        }
    }
}
