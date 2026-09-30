import { useState } from "react";
import {
  FiLogOut,
  FiRefreshCw,
  FiSend,
  FiTrash2,
  FiUserCheck,
  FiUserMinus,
  FiUserPlus,
  FiX,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  Badge,
  ConfirmDialog,
  DataTable,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  dangerButton,
  fieldLabel,
  inputControl,
  panelNote,
  primaryButton,
  rowAction,
  rowActionDanger,
  secondaryButton,
  toolbarSelect,
} from "../../../components/workspace/pastel";
import {
  useChangeMemberRoleMutation,
  useDeleteBusinessMutation,
  useGetInvitationsQuery,
  useGetMembersQuery,
  useInviteMemberMutation,
  useLeaveBusinessMutation,
  useRemoveMemberMutation,
  useResendInvitationMutation,
  useRevokeInvitationMutation,
  useTransferBusinessMutation,
} from "../../business/businessApiSlice";
import { ROLE_LABELS } from "../../business/components/BusinessSwitcher";
import {
  ROLE_SUMMARIES,
  RefusalNote,
  useBusiness,
} from "../../business/permissions";
import useEnterBusiness from "../../business/useEnterBusiness";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import SettingsTabs from "../components/SettingsTabs";
import TeamActivity from "../components/TeamActivity";

const ROLES = ["ADMIN", "MEMBER", "VIEWER"];

const nameOf = (member) =>
  [member.firstName, member.lastName].filter(Boolean).join(" ") || member.email;

// Deleting a business can't be undone, so its name is typed to confirm.
const DeleteBusinessDialog = ({ open, name, busy, onConfirm, onClose }) => {
  const [typed, setTyped] = useState("");
  const close = () => {
    setTyped("");
    onClose();
  };
  return (
    <Dialog
      open={open}
      title="Delete this business?"
      onClose={close}
      busy={busy}
    >
      <p className="text-[14px] leading-[1.6] text-spot-body">
        {name} and everything in it are deleted for everyone: its documents,
        clients, items, payments and settings. This can’t be undone.
      </p>
      <label className={`${fieldLabel} mt-5 block`} htmlFor="confirm-business">
        Type <strong>{name}</strong> to confirm
      </label>
      <input
        id="confirm-business"
        className={inputControl}
        value={typed}
        onChange={(event) => setTyped(event.target.value)}
        autoComplete="off"
        disabled={busy}
        data-autofocus
      />
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button
          type="button"
          className={secondaryButton}
          onClick={close}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="button"
          className={dangerButton}
          onClick={onConfirm}
          disabled={busy || typed.trim() !== name}
        >
          <FiTrash2 aria-hidden="true" />
          {busy ? "Deleting…" : "Delete business"}
        </button>
      </div>
    </Dialog>
  );
};

// An invitation goes by email; whoever accepts it with that address joins in
// the role chosen here.
const InviteDialog = ({ open, business, onClose }) => {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("MEMBER");
  const [invite, { isLoading }] = useInviteMemberMutation();
  const close = () => {
    setEmail("");
    setRole("MEMBER");
    onClose();
  };
  const send = async (event) => {
    event.preventDefault();
    try {
      const invitation = await invite({ email: email.trim(), role }).unwrap();
      toast.success(`Invitation sent to ${invitation.email}.`);
      close();
    } catch (error) {
      toast.error(errorMessage(error, "The invitation could not be sent."));
    }
  };
  return (
    <Dialog
      open={open}
      title={`Invite someone to ${business}`}
      onClose={close}
      busy={isLoading}
      dismissible={false}
    >
      <form className="mt-5 flex flex-col gap-4" onSubmit={send}>
        <Field
          label="Email"
          type="email"
          required
          maxLength={254}
          autoComplete="off"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={isLoading}
          data-autofocus
        />
        <Field
          as="select"
          label="Role"
          value={role}
          onChange={(event) => setRole(event.target.value)}
          hint={ROLE_SUMMARIES[role]}
          disabled={isLoading}
        >
          {ROLES.map((option) => (
            <option key={option} value={option}>
              {ROLE_LABELS[option]}
            </option>
          ))}
        </Field>
        <p className={panelNote}>
          The link in the email is open for 7 days. They join once they accept
          it signed in with this address.
        </p>
        <div className="mt-1 flex flex-wrap justify-end gap-2">
          <button
            type="button"
            className={secondaryButton}
            onClick={close}
            disabled={isLoading}
          >
            Cancel
          </button>
          <button type="submit" className={primaryButton} disabled={isLoading}>
            <FiSend aria-hidden="true" />
            {isLoading ? "Sending…" : "Send invitation"}
          </button>
        </div>
      </form>
    </Dialog>
  );
};

// Invitations no one has answered yet. Resending mails a new link and the
// old one stops working; an expired invitation can be resent too.
const Invitations = ({ business }) => {
  const query = useGetInvitationsQuery();
  const invitations = query.data?.invitations || [];
  const [resend, { isLoading: resending }] = useResendInvitationMutation();
  const [revoke, { isLoading: revoking }] = useRevokeInvitationMutation();
  const act = async (action, success, failure) => {
    try {
      await action();
      toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error, failure));
    }
  };
  if (query.isError)
    return <ErrorState error={query.error} retry={query.refetch} />;
  if (!invitations.length) return null;
  const columns = [
    {
      label: "Invited",
      type: "main",
      render: (invitation) => (
        <span className="flex flex-col gap-0.5">
          <span className="[overflow-wrap:anywhere]">{invitation.email}</span>
          {invitation.invitedBy && (
            <span className={`font-normal ${panelNote}`}>
              By {invitation.invitedBy}
            </span>
          )}
        </span>
      ),
    },
    { label: "Role", render: (invitation) => ROLE_LABELS[invitation.role] },
    {
      label: "Open until",
      render: (invitation) =>
        invitation.expired ? (
          <Badge tone="unpaid">Expired</Badge>
        ) : (
          dateLabel(invitation.expiresAt)
        ),
    },
    {
      label: "Resend",
      type: "action",
      render: (invitation) => (
        <button
          type="button"
          className={rowAction}
          onClick={() =>
            act(
              () => resend(invitation._id).unwrap(),
              `A new link was sent to ${invitation.email}.`,
              "The invitation could not be sent again.",
            )
          }
          disabled={resending || revoking}
          aria-label={`Resend the invitation to ${invitation.email}`}
        >
          <FiRefreshCw aria-hidden="true" />
          <span>Resend</span>
        </button>
      ),
    },
    {
      label: "Withdraw",
      type: "action",
      render: (invitation) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() =>
            act(
              () => revoke(invitation._id).unwrap(),
              `The invitation to ${invitation.email} was withdrawn.`,
              "The invitation could not be withdrawn.",
            )
          }
          disabled={resending || revoking}
          aria-label={`Withdraw the invitation to ${invitation.email}`}
        >
          <FiX aria-hidden="true" />
          <span>Withdraw</span>
        </button>
      ),
    },
  ];
  return (
    <Panel
      title="Invitations"
      description={`Waiting for an answer. Each joins ${business} in the role shown once accepted.`}
    >
      <DataTable
        columns={columns}
        rows={invitations}
        label="Invitations"
        busy={query.isFetching}
      />
    </Panel>
  );
};

// The people who work in the business, their roles, and the ways out: leaving
// it, handing it over, or deleting it.
const TeamPage = () => {
  useTitle("Team — Invoice Spot");
  const { business, reason, can } = useBusiness();
  const query = useGetMembersQuery();
  const members = query.data?.members || [];
  const teamRefusal = reason("MANAGE_TEAM");
  const owner = business?.role === "OWNER";
  const demo = useGetUserProfileQuery().data?.userProfile?.demo;
  const deleteNote = demo
    ? "Deleting the business is turned off for the shared demo account."
    : undefined;
  const inviteNote =
    teamRefusal ||
    (demo
      ? "Inviting people is turned off for the shared demo account."
      : undefined);
  const [inviting, setInviting] = useState(false);
  const [changeRole, { isLoading: changing }] = useChangeMemberRoleMutation();
  const [removeMember, { isLoading: removing }] = useRemoveMemberMutation();
  const [leave, { isLoading: leaving }] = useLeaveBusinessMutation();
  const [transfer, { isLoading: transferring }] = useTransferBusinessMutation();
  const [deleteBusiness, { isLoading: deleting }] = useDeleteBusinessMutation();
  const enter = useEnterBusiness();
  const [confirming, setConfirming] = useState(null);
  const [successor, setSuccessor] = useState("");
  const others = members.filter((member) => !member.you);

  const run = async (action, success, failure) => {
    try {
      await action();
      toast.success(success);
      return true;
    } catch (error) {
      toast.error(errorMessage(error, failure));
      return false;
    }
  };
  const confirm = async () => {
    const { kind, member } = confirming;
    if (kind === "remove") {
      if (
        await run(
          () => removeMember(member._id).unwrap(),
          `${nameOf(member)} is no longer part of ${business.name}.`,
          "They could not be removed.",
        )
      ) {
        setConfirming(null);
      }
    } else if (kind === "leave") {
      if (
        await run(
          () => leave().unwrap(),
          `You left ${business.name}.`,
          "You could not leave the business.",
        )
      ) {
        setConfirming(null);
        enter(null);
      }
    } else if (kind === "transfer") {
      if (
        await run(
          () => transfer(member._id).unwrap(),
          `${nameOf(member)} now owns ${business.name}. You’re an admin.`,
          "The business could not be handed over.",
        )
      ) {
        setConfirming(null);
        setSuccessor("");
      }
    }
  };
  const remove = async () => {
    if (
      await run(
        () => deleteBusiness().unwrap(),
        `${business.name} was deleted.`,
        "The business could not be deleted.",
      )
    ) {
      setConfirming(null);
      enter(null);
    }
  };

  const columns = [
    {
      label: "Member",
      type: "main",
      render: (member) => (
        <span className="flex flex-col gap-0.5">
          <span className="flex flex-wrap items-center gap-1.5">
            {nameOf(member)}
            {member.you && <Badge>You</Badge>}
            {!member.active && <Badge tone="unpaid">Deactivated</Badge>}
          </span>
          <span className={`font-normal ${panelNote}`}>{member.email}</span>
        </span>
      ),
    },
    {
      label: "Role",
      render: (member) =>
        member.role === "OWNER" || member.you || teamRefusal ? (
          ROLE_LABELS[member.role]
        ) : (
          <select
            className={toolbarSelect}
            value={member.role}
            onChange={(event) =>
              run(
                () =>
                  changeRole({
                    userId: member._id,
                    role: event.target.value,
                  }).unwrap(),
                `${nameOf(member)} is now ${ROLE_LABELS[event.target.value].toLowerCase()}.`,
                "The role could not be changed.",
              )
            }
            disabled={changing}
            aria-label={`Role of ${nameOf(member)}`}
          >
            {ROLES.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </select>
        ),
    },
    { label: "Joined", render: (member) => dateLabel(member.joinedAt) },
    {
      label: "Remove",
      type: "action",
      render: (member) =>
        member.role !== "OWNER" &&
        !member.you && (
          <button
            type="button"
            className={rowActionDanger}
            onClick={() => setConfirming({ kind: "remove", member })}
            disabled={removing || Boolean(teamRefusal)}
            title={teamRefusal}
            aria-describedby={teamRefusal ? "team-refusal" : undefined}
            aria-label={`Remove ${nameOf(member)}`}
          >
            <FiUserMinus aria-hidden="true" />
            <span>Remove</span>
          </button>
        ),
    },
  ];

  const dialog =
    confirming &&
    {
      remove: {
        title: "Remove this member?",
        label: "Remove",
        text: `${nameOf(confirming.member || {})} loses ${business?.name} at once.`,
      },
      leave: {
        title: `Leave ${business?.name}?`,
        label: "Leave business",
        text: "You lose access at once. Someone would have to invite you again.",
      },
      transfer: {
        title: "Hand this business over?",
        label: "Hand over",
        text: `${nameOf(confirming.member || {})} becomes the owner of ${business?.name}, and you stay on as an admin.`,
      },
    }[confirming.kind];

  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="The people who work in this business, and what each may do."
        actions={<SettingsTabs />}
      />
      <RefusalNote id="team-refusal" reason={teamRefusal} />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel
          title="Team"
          description={`Everyone who works in ${business?.name}. Owners and admins manage the team; members work on its records; viewers only look.`}
          aside={
            <button
              type="button"
              className={primaryButton}
              onClick={() => setInviting(true)}
              disabled={Boolean(inviteNote)}
              title={inviteNote}
              aria-describedby={inviteNote ? "invite-note" : undefined}
            >
              <FiUserPlus aria-hidden="true" /> Invite
            </button>
          }
        >
          <DataTable columns={columns} rows={members} label="Team" />
        </Panel>
      )}
      <RefusalNote id="invite-note" reason={inviteNote} />
      {!teamRefusal && business && <Invitations business={business.name} />}
      {business && can("VIEW_TEAM_ACTIVITY") && (
        <TeamActivity business={business.name} />
      )}
      {!owner && business && (
        <Panel
          title="Leave this business"
          description="You keep your account and your own businesses."
          aside={
            <button
              type="button"
              className={secondaryButton}
              onClick={() => setConfirming({ kind: "leave" })}
              disabled={leaving}
            >
              <FiLogOut aria-hidden="true" /> Leave {business.name}
            </button>
          }
        />
      )}
      {owner && (
        <section className="flex flex-col gap-5 rounded-[30px] border border-white/90 bg-overdue/60 p-6 compact:p-[18px] max-[700px]:rounded-3xl max-[700px]:p-[18px]">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="min-w-0">
              <h2 className="font-display text-[22px] font-light tracking-[-0.02em] text-overdue-ink">
                Hand over the business
              </h2>
              <p className="mt-1.5 text-[13.5px] text-overdue-muted">
                {others.length
                  ? "Make another member the owner. You stay on as an admin."
                  : "Invite someone first; only a member can take it over."}
              </p>
            </div>
            {others.length > 0 && (
              <div className="flex flex-wrap items-center gap-2">
                <select
                  className={toolbarSelect}
                  value={successor}
                  onChange={(event) => setSuccessor(event.target.value)}
                  aria-label="New owner"
                >
                  <option value="">Choose a member</option>
                  {others.map((member) => (
                    <option key={member._id} value={member._id}>
                      {nameOf(member)}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className={secondaryButton}
                  onClick={() =>
                    setConfirming({
                      kind: "transfer",
                      member: others.find((member) => member._id === successor),
                    })
                  }
                  disabled={!successor || transferring}
                >
                  <FiUserCheck aria-hidden="true" /> Hand over
                </button>
              </div>
            )}
          </div>
          <div className="flex flex-wrap items-end justify-between gap-4 border-t border-overdue-icon/15 pt-5">
            <div className="min-w-0">
              <h2 className="font-display text-[22px] font-light tracking-[-0.02em] text-overdue-ink">
                Delete the business
              </h2>
              <p className="mt-1.5 text-[13.5px] text-overdue-muted">
                Deletes {business?.name} and everything in it, for everyone.
              </p>
            </div>
            <button
              type="button"
              className={dangerButton}
              onClick={() => setConfirming({ kind: "delete" })}
              disabled={Boolean(deleteNote)}
              title={deleteNote}
              aria-describedby={deleteNote ? "delete-business-note" : undefined}
            >
              <FiTrash2 aria-hidden="true" /> Delete business
            </button>
          </div>
        </section>
      )}
      <RefusalNote id="delete-business-note" reason={deleteNote} />
      <ConfirmDialog
        open={Boolean(dialog)}
        title={dialog?.title}
        confirmLabel={dialog?.label}
        onConfirm={confirm}
        onClose={() => setConfirming(null)}
        busy={removing || leaving || transferring}
      >
        {dialog?.text}
      </ConfirmDialog>
      <InviteDialog
        open={inviting}
        business={business?.name || ""}
        onClose={() => setInviting(false)}
      />
      <DeleteBusinessDialog
        open={confirming?.kind === "delete"}
        name={business?.name || ""}
        busy={deleting}
        onConfirm={remove}
        onClose={() => setConfirming(null)}
      />
    </WorkspacePage>
  );
};

export default TeamPage;
