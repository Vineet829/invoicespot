package com.invoicespot.business;

// What a member may do in a business, and what they're told when their role
// doesn't allow it.
public enum Permission {
    VIEW_RECORDS("You can't see this business's records"),
    EXPORT_LISTS("You can't download this business's records"),
    EDIT_RECORDS("Viewers can't change anything in this business"),
    DELETE_RECORDS("Only the owner and admins can delete anything in this business"),
    EXPORT_ARCHIVE("Only the owner and admins can download everything"),
    MANAGE_BUSINESS("Only the owner and admins can change the business's details and settings"),
    MANAGE_TEAM("Only the owner and admins can manage the team"),
    VIEW_TEAM_ACTIVITY("Only the owner and admins can see the team's activity"),
    OWN_BUSINESS("Only the owner can do that");

    private final String refusal;

    Permission(String refusal) {
        this.refusal = refusal;
    }

    public String refusal() {
        return refusal;
    }
}
