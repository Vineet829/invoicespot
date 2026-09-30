import { useEffect, useState } from "react";
import QRCode from "qrcode";
import {
  FiCopy,
  FiDownload,
  FiRefreshCw,
  FiShield,
  FiShieldOff,
} from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Badge,
  ErrorState,
  Field,
  LoadingState,
  Panel,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  dangerButton,
  panelNote,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { saveFile } from "../../documents/useDocumentPdf";
import {
  useDisableTwoFactorMutation,
  useEnableTwoFactorMutation,
  useGetTwoFactorQuery,
  useRenewRecoveryCodesMutation,
  useSetupTwoFactorMutation,
} from "../securityApiSlice";

const dialogText = "mt-3 text-[14px] leading-[1.6] text-spot-body";
const dialogActions = "mt-6 flex flex-wrap justify-end gap-2.5";

// The codes are shown once: they can be saved as a file or copied.
const RecoveryCodes = ({ codes, onDone }) => (
  <>
    <p className={dialogText}>
      Each code signs you in once if you can’t use your authenticator app. Keep
      them somewhere safe; you won’t see them again.
    </p>
    <ol
      className="m-0 mt-4 grid list-none grid-cols-2 gap-x-6 gap-y-2 rounded-2xl bg-spot-thead p-4 font-mono text-[14px] tracking-[0.04em] text-spot-ink max-[420px]:grid-cols-1"
      aria-label="Recovery codes"
    >
      {codes.map((code) => (
        <li key={code}>{code}</li>
      ))}
    </ol>
    <div className={dialogActions}>
      <button
        type="button"
        className={secondaryButton}
        onClick={() =>
          saveFile(
            URL.createObjectURL(
              new Blob(
                [`Invoice Spot recovery codes\n\n${codes.join("\n")}\n`],
                {
                  type: "text/plain",
                },
              ),
            ),
            "invoice-spot-recovery-codes.txt",
          )
        }
      >
        <FiDownload aria-hidden="true" /> Download
      </button>
      <button
        type="button"
        className={secondaryButton}
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(codes.join("\n"));
            toast.success("The recovery codes were copied");
          } catch {
            toast.error(
              "The codes could not be copied. Download them instead.",
            );
          }
        }}
      >
        <FiCopy aria-hidden="true" /> Copy
      </button>
      <button type="button" className={primaryButton} onClick={onDone}>
        Done
      </button>
    </div>
  </>
);

// Password, then the app, then the recovery codes.
const TurnOn = ({ onClose, onBusy }) => {
  const [password, setPassword] = useState("");
  const [setup, setSetup] = useState(null);
  const [image, setImage] = useState(null);
  const [code, setCode] = useState("");
  const [codes, setCodes] = useState(null);
  const [start, { isLoading: starting }] = useSetupTwoFactorMutation();
  const [enable, { isLoading: enabling }] = useEnableTwoFactorMutation();
  useEffect(() => onBusy(starting || enabling), [onBusy, starting, enabling]);
  useEffect(() => {
    if (!setup) return;
    QRCode.toDataURL(setup.otpauthUri, {
      margin: 1,
      width: 192,
      color: { dark: "#30234f", light: "#ffffff" },
    }).then(setImage, () => setImage(null));
  }, [setup]);
  if (codes) return <RecoveryCodes codes={codes} onDone={onClose} />;
  if (!setup)
    return (
      <form
        onSubmit={async (event) => {
          event.preventDefault();
          try {
            setSetup(await start(password).unwrap());
          } catch (error) {
            toast.error(
              errorMessage(error, "Two-factor sign-in could not start."),
            );
          }
        }}
      >
        <p className={dialogText}>
          First, confirm it’s you. You’ll then scan a code with an authenticator
          app such as Google Authenticator, 1Password or Microsoft
          Authenticator.
        </p>
        <div className="mt-5">
          <Field
            label="Your password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={starting}
            data-autofocus
          />
        </div>
        <div className={dialogActions}>
          <button type="button" className={secondaryButton} onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            className={primaryButton}
            disabled={starting || !password}
          >
            {starting ? "Checking…" : "Continue"}
          </button>
        </div>
      </form>
    );
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault();
        try {
          setCodes((await enable(code).unwrap()).recoveryCodes);
        } catch (error) {
          toast.error(errorMessage(error, "That code didn't work."));
        }
      }}
    >
      <p className={dialogText}>
        Scan this with your authenticator app, then enter the 6-digit code it
        shows.
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-5">
        <span className="grid size-[192px] shrink-0 place-items-center rounded-2xl border border-[#23273514] bg-white">
          {image && (
            <img
              src={image}
              width="176"
              height="176"
              alt="QR code to scan with your authenticator app"
            />
          )}
        </span>
        <p className={`min-w-0 flex-1 basis-[180px] ${panelNote}`}>
          Can’t scan it? Enter this key in the app instead:
          <code className="mt-2 block font-mono text-[14px] tracking-[0.06em] text-spot-ink">
            {setup.secret.match(/.{1,4}/g).join(" ")}
          </code>
        </p>
      </div>
      <div className="mt-5">
        <Field
          label="Code from the app"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          required
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
          disabled={enabling}
        />
      </div>
      <div className={dialogActions}>
        <button type="button" className={secondaryButton} onClick={onClose}>
          Cancel
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={enabling || code.length !== 6}
        >
          {enabling ? "Turning on…" : "Turn on"}
        </button>
      </div>
    </form>
  );
};

const TurnOff = ({ onClose, onBusy }) => {
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [disable, { isLoading }] = useDisableTwoFactorMutation();
  useEffect(() => onBusy(isLoading), [onBusy, isLoading]);
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault();
        try {
          toast.success((await disable({ password, code }).unwrap()).message);
          onClose();
        } catch (error) {
          toast.error(
            errorMessage(error, "Two-factor sign-in could not be turned off."),
          );
        }
      }}
    >
      <p className={dialogText}>
        Your password alone will sign you in again. Confirm with your password
        and a code from your app, or a recovery code.
      </p>
      <div className="mt-5 flex flex-col gap-4">
        <Field
          label="Your password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={isLoading}
          data-autofocus
        />
        <Field
          label="Code"
          autoComplete="one-time-code"
          maxLength={11}
          required
          value={code}
          onChange={(event) => setCode(event.target.value)}
          disabled={isLoading}
          hint="6 digits from the app, or a recovery code."
        />
      </div>
      <div className={dialogActions}>
        <button type="button" className={secondaryButton} onClick={onClose}>
          Cancel
        </button>
        <button
          type="submit"
          className={dangerButton}
          disabled={isLoading || !password || !code}
        >
          {isLoading ? "Turning off…" : "Turn off"}
        </button>
      </div>
    </form>
  );
};

const NewCodes = ({ onClose, onBusy }) => {
  const [code, setCode] = useState("");
  const [codes, setCodes] = useState(null);
  const [renew, { isLoading }] = useRenewRecoveryCodesMutation();
  useEffect(() => onBusy(isLoading), [onBusy, isLoading]);
  if (codes) return <RecoveryCodes codes={codes} onDone={onClose} />;
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault();
        try {
          setCodes((await renew(code).unwrap()).recoveryCodes);
        } catch (error) {
          toast.error(errorMessage(error, "That code didn't work."));
        }
      }}
    >
      <p className={dialogText}>
        New codes replace your old ones, which stop working. Enter a code from
        your app to continue.
      </p>
      <div className="mt-5">
        <Field
          label="Code from the app"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          required
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
          disabled={isLoading}
          data-autofocus
        />
      </div>
      <div className={dialogActions}>
        <button type="button" className={secondaryButton} onClick={onClose}>
          Cancel
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={isLoading || code.length !== 6}
        >
          {isLoading ? "Checking…" : "Get new codes"}
        </button>
      </div>
    </form>
  );
};

const DIALOGS = {
  on: { title: "Turn on two-factor sign-in", Step: TurnOn },
  off: { title: "Turn off two-factor sign-in", Step: TurnOff },
  codes: { title: "New recovery codes", Step: NewCodes },
};

// A code from an authenticator app as well as the password at sign-in.
const TwoFactor = ({ demo }) => {
  const query = useGetTwoFactorQuery();
  const status = query.data;
  const [open, setOpen] = useState(null);
  const [busy, setBusy] = useState(false);
  const dialog = open && DIALOGS[open];
  const close = () => setOpen(null);
  return (
    <Panel
      title="Two-factor sign-in"
      description="A code from an authenticator app as well as your password when you log in."
      aside={
        status && (
          <Badge tone={status.enabled ? "paid" : "neutral"}>
            {status.enabled ? "On" : "Off"}
          </Badge>
        )
      }
    >
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : status.enabled ? (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className={panelNote}>
            On since {dateLabel(status.enabledAt)} · {status.recoveryCodesLeft}{" "}
            of 10 recovery codes left
          </p>
          <div className="flex flex-wrap gap-2.5 max-[700px]:w-full max-[700px]:[&>*]:flex-1">
            <button
              type="button"
              className={secondaryButton}
              onClick={() => setOpen("codes")}
            >
              <FiRefreshCw aria-hidden="true" /> New recovery codes
            </button>
            <button
              type="button"
              className={dangerButton}
              onClick={() => setOpen("off")}
            >
              <FiShieldOff aria-hidden="true" /> Turn off
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className={`max-w-[560px] ${panelNote}`}>
            {demo
              ? "This is a shared demo account, so two-factor sign-in is turned off."
              : status.available
                ? "Once it’s on, signing in asks for the 6-digit code your app shows. Recovery codes sign you in if you lose your phone."
                : "Two-factor sign-in isn’t available on this server yet."}
          </p>
          <button
            type="button"
            className={`${primaryButton} max-[700px]:w-full`}
            onClick={() => setOpen("on")}
            disabled={demo || !status.available}
          >
            <FiShield aria-hidden="true" /> Turn on
          </button>
        </div>
      )}
      <Dialog
        open={Boolean(dialog)}
        title={dialog?.title}
        onClose={close}
        busy={busy}
        dismissible={false}
        size="medium"
      >
        {dialog && <dialog.Step onClose={close} onBusy={setBusy} />}
      </Dialog>
    </Panel>
  );
};

export default TwoFactor;
