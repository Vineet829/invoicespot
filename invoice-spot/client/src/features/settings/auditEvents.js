// What each audit log entry means, in the words the app uses.
export const EVENT_LABELS = {
  SIGN_IN: "Signed in",
  SIGN_IN_FAILED: "Sign-in failed",
  SIGN_OUT: "Signed out",
  SESSION_TOKEN_REUSED: "Signed out everywhere",
  SESSION_SIGNED_OUT: "Signed out a device",
  OTHER_SESSIONS_SIGNED_OUT: "Signed out other devices",
  PASSWORD_RESET: "Password reset by email",
  PASSWORD_CHANGED: "Password changed",
  TWO_FACTOR_ENABLED: "Two-factor sign-in turned on",
  TWO_FACTOR_DISABLED: "Two-factor sign-in turned off",
  RECOVERY_CODES_RENEWED: "New recovery codes",
  RECOVERY_CODE_USED: "Recovery code used",
  ACCOUNT_DELETED: "Account deleted",
  USER_DEACTIVATED: "Deactivated by an admin",
  USER_REACTIVATED: "Reactivated by an admin",
  USER_DELETED: "Deleted by an admin",
  ADMIN_GRANTED: "Made an admin",
  ADMIN_REVOKED: "Admin role removed",
  BUSINESS_CREATED: "Business started",
  MEMBER_ROLE_CHANGED: "Role changed",
  MEMBER_REMOVED: "Removed from the business",
  MEMBER_LEFT: "Left the business",
  OWNERSHIP_TRANSFERRED: "Business handed over",
  BUSINESS_DELETED: "Business deleted",
  MEMBER_INVITED: "Invitation sent",
  INVITATION_RESENT: "Invitation sent again",
  INVITATION_REVOKED: "Invitation withdrawn",
  MEMBER_JOINED: "Joined the business",
};

// Entries that may mean someone else is trying the account.
export const WARNING_EVENTS = new Set([
  "SIGN_IN_FAILED",
  "SESSION_TOKEN_REUSED",
]);

export const eventLabel = (type) => EVENT_LABELS[type] || type;

export const whenLabel = (value) => {
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? date.toLocaleString("en", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
      })
    : "—";
};
