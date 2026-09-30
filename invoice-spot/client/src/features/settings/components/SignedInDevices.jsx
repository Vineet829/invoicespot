import { useState } from "react";
import { FiLogOut, FiMonitor, FiSmartphone, FiTablet } from "react-icons/fi";
import { toast } from "react-toastify";
import {
  Badge,
  ConfirmDialog,
  ErrorState,
  LoadingState,
  Panel,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  rowActionDanger,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { timeAgo } from "../../notifications/timeAgo";
import {
  useGetSessionsQuery,
  useSignOutOtherSessionsMutation,
  useSignOutSessionMutation,
} from "../securityApiSlice";

const deviceIcon = (device) =>
  /iPhone|Android/.test(device)
    ? FiSmartphone
    : /iPad/.test(device)
      ? FiTablet
      : FiMonitor;

const since = (value) => {
  const ago = timeAgo(value);
  return ago === "Just now" || ago === "Yesterday" ? ago.toLowerCase() : ago;
};

// Where the account is signed in. Signing a device out ends its session at
// once, whatever it is doing; this device signs out with Log out.
const SignedInDevices = ({ demo }) => {
  const query = useGetSessionsQuery(undefined, {
    refetchOnMountOrArgChange: true,
  });
  const [signOut, { isLoading: signingOut }] = useSignOutSessionMutation();
  const [signOutOthers, { isLoading: signingOutOthers }] =
    useSignOutOtherSessionsMutation();
  const [confirming, setConfirming] = useState(false);
  const sessions = query.data?.sessions || [];
  const others = sessions.filter((session) => !session.current);
  const run = async (action, failure) => {
    try {
      toast.success((await action().unwrap()).message);
      return true;
    } catch (error) {
      toast.error(errorMessage(error, failure));
      return false;
    }
  };
  return (
    <Panel
      title="Signed-in devices"
      description="Where your account is signed in now. Signing a device out ends its session at once."
      aside={
        <button
          type="button"
          className={`${secondaryButton} shrink-0`}
          onClick={() => setConfirming(true)}
          disabled={demo || others.length === 0 || signingOutOthers}
        >
          <FiLogOut aria-hidden="true" /> Sign out other devices
        </button>
      }
    >
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <ul className="m-0 list-none p-0">
          {sessions.map((session) => {
            const Icon = deviceIcon(session.device);
            return (
              <li
                key={session._id}
                className="flex items-center gap-4 py-3.5 first:pt-0 last:pb-0 [&:not(:first-child)]:border-t [&:not(:first-child)]:border-[#23273512]"
              >
                <span
                  className="grid size-10 shrink-0 place-items-center rounded-xl bg-spot-violet/[.08] text-spot-violet [&>svg]:size-[18px] [&>svg]:stroke-[1.7]"
                  aria-hidden="true"
                >
                  <Icon />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2 text-[14.5px] font-medium text-spot-ink">
                    {session.device}
                    {session.current && <Badge tone="paid">This device</Badge>}
                  </span>
                  <span className={`mt-0.5 block ${panelNote}`}>
                    {[
                      session.ip,
                      `signed in ${dateLabel(session.signedInAt)}`,
                      session.current
                        ? "active now"
                        : `last active ${since(session.lastActiveAt)}`,
                    ]
                      .filter(Boolean)
                      .join(" · ")
                      .replace(/^./, (first) => first.toUpperCase())}
                  </span>
                </span>
                {!session.current && (
                  <button
                    type="button"
                    className={rowActionDanger}
                    onClick={() =>
                      run(
                        () => signOut(session._id),
                        "That device could not be signed out.",
                      )
                    }
                    disabled={signingOut}
                    aria-label={`Sign out ${session.device}, last active ${since(session.lastActiveAt)}`}
                  >
                    <FiLogOut aria-hidden="true" />
                    <span>Sign out</span>
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {demo && (
        <p className={`mt-4 ${panelNote}`}>
          This is a shared demo account, so other visitors’ devices aren’t
          shown.
        </p>
      )}
      <ConfirmDialog
        open={confirming}
        title="Sign out other devices?"
        confirmLabel="Sign out other devices"
        onConfirm={async () => {
          if (
            await run(
              () => signOutOthers(),
              "The other devices could not be signed out.",
            )
          )
            setConfirming(false);
        }}
        onClose={() => setConfirming(false)}
        busy={signingOutOthers}
      >
        Every other browser signed in to your account is signed out at once.
        This one stays signed in.
      </ConfirmDialog>
    </Panel>
  );
};

export default SignedInDevices;
