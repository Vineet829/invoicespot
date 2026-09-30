package com.invoicespot.audit;

import java.util.EnumSet;
import java.util.Set;

public enum AuditEventType {
    SIGN_IN,
    SIGN_IN_FAILED,
    SIGN_OUT,
    SESSION_TOKEN_REUSED,
    SESSION_SIGNED_OUT,
    OTHER_SESSIONS_SIGNED_OUT,
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    TWO_FACTOR_ENABLED,
    TWO_FACTOR_DISABLED,
    RECOVERY_CODES_RENEWED,
    RECOVERY_CODE_USED,
    ACCOUNT_DELETED,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    ADMIN_GRANTED,
    ADMIN_REVOKED,
    USER_DELETED,
    BUSINESS_CREATED,
    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    OWNERSHIP_TRANSFERRED,
    BUSINESS_DELETED,
    MEMBER_INVITED,
    INVITATION_RESENT,
    INVITATION_REVOKED,
    MEMBER_JOINED;

    private static final Set<AuditEventType> ABOUT_A_BUSINESS = EnumSet.of(
            BUSINESS_CREATED,
            MEMBER_ROLE_CHANGED,
            MEMBER_REMOVED,
            MEMBER_LEFT,
            OWNERSHIP_TRANSFERRED,
            BUSINESS_DELETED,
            MEMBER_INVITED,
            INVITATION_RESENT,
            INVITATION_REVOKED,
            MEMBER_JOINED);

    // Events about a business and its team, rather than one account's
    // sign-ins and security.
    public static Set<AuditEventType> aboutABusiness() {
        return ABOUT_A_BUSINESS;
    }
}
