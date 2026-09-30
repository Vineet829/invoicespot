import { useMemo } from "react";
import { useGetBusinessesQuery } from "./businessApiSlice";

// What the server answers when a role doesn't allow something, shown on the
// controls it disables.
export const REFUSALS = {
  EDIT_RECORDS: "Viewers can't change anything in this business",
  DELETE_RECORDS: "Only the owner and admins can delete documents and clients",
  EXPORT_ARCHIVE: "Only the owner and admins can download everything",
  MANAGE_BUSINESS:
    "Only the owner and admins can change the business's details and settings",
  MANAGE_TEAM: "Only the owner and admins can manage the team",
  VIEW_TEAM_ACTIVITY: "Only the owner and admins can see the team's activity",
  OWN_BUSINESS: "Only the owner can do that",
};

// What each role someone can be invited as allows, as the server's rules
// grant it.
export const ROLE_SUMMARIES = {
  ADMIN: "Everything except handing the business over or deleting it.",
  MEMBER:
    "Creates, edits and emails documents, and works on clients, items and payments. Can't delete documents or clients, download everything, or manage the business or its team.",
  VIEWER: "Sees everything and exports lists, but changes nothing.",
};

// The business this tab works in and what the member's role allows there.
// `reason(permission)` is the refusal to show, or nothing when allowed. The
// server enforces every rule; this only keeps the screens honest.
export const useBusiness = () => {
  const business = useGetBusinessesQuery().data?.businesses?.find(
    (candidate) => candidate.active,
  );
  return useMemo(() => {
    const permissions = business?.permissions;
    const can = (permission) =>
      !permissions || permissions.includes(permission);
    return {
      business,
      role: business?.role,
      can,
      reason: (permission) =>
        can(permission) ? undefined : REFUSALS[permission],
    };
  }, [business]);
};

// The reason a control is disabled, for screen readers; the control points at
// it with aria-describedby.
export const RefusalNote = ({ id, reason }) =>
  reason ? (
    <span id={id} className="sr-only">
      {reason}
    </span>
  ) : null;
