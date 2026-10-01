import {
  strengthColor,
  strengthIndicator,
} from "../../../utils/password-strength";
import {
  appSurface,
  iconButton,
  inputControl,
  surface,
  textLink,
} from "../../../components/workspace/pastel";

// Log in and sign up in the workspace's pastel style: AuthVisual on the left,
// the form panel on the right. Below 901px only the form remains. From 901px
// both panels' content is zoomed to 140%, and the page scrolls when it is
// taller than the window.
const button =
  "relative inline-flex min-h-11 w-full min-w-0 items-center justify-center gap-2 overflow-hidden rounded-xl px-3.5 py-3 text-[14px] font-medium leading-[1.3] transition-[transform,box-shadow,background-color,border-color,color] duration-200 after:pointer-events-none after:absolute after:inset-0 after:content-[''] after:[translate:-100%_0] hover:enabled:after:animate-auth-sheen disabled:cursor-progress disabled:opacity-60 [&>svg]:size-4 [&>svg]:shrink-0 [&>svg]:transition-transform [&>svg]:duration-200";
const underlinedLinks =
  "[&_a:hover]:text-spot-ink [&_a]:text-spot-text [&_a]:underline [&_a]:decoration-spot-text/35 [&_a]:underline-offset-2";

export const auth = {
  page: `${appSurface} grid min-h-dvh grid-cols-2 grid-rows-[minmax(0,1fr)] gap-[clamp(16px,1.5vw,24px)] p-[clamp(20px,2vw,32px)] max-[900px]:min-h-svh max-[900px]:grid-cols-1 max-[900px]:grid-rows-[auto] max-[900px]:p-[clamp(12px,3.5vw,24px)]`,
  formPanel: `${surface} relative flex min-h-0 min-w-0 flex-col overflow-y-auto px-[clamp(32px,4vw,72px)] py-9 [scrollbar-color:#a996e080_transparent] [scrollbar-width:thin] before:pointer-events-none before:absolute before:bottom-0 before:left-0 before:h-80 before:w-[min(320px,100%)] before:rounded-full before:bg-spot-lilac/25 before:blur-[80px] before:content-[''] max-[900px]:overflow-visible max-[900px]:pb-[max(36px,env(safe-area-inset-bottom))] max-[900px]:pl-[max(clamp(20px,5vw,36px),env(safe-area-inset-left))] max-[900px]:pr-[max(clamp(20px,5vw,36px),env(safe-area-inset-right))] max-[900px]:pt-[max(36px,env(safe-area-inset-top))] max-[700px]:rounded-3xl`,
  formWrap:
    "relative z-[1] m-auto w-full max-w-[400px] min-[901px]:[zoom:1.4] max-[900px]:max-w-[480px]",
  formWrapWide:
    "relative z-[1] m-auto w-full max-w-[440px] min-[901px]:[zoom:1.4] max-[900px]:max-w-[480px]",
  mobileBrand:
    "mb-8 hidden items-center gap-2.5 font-display text-[16px] leading-none tracking-[-0.025em] text-spot-ink max-[900px]:inline-flex",
  brandMark:
    "grid size-8 place-items-center rounded-[9px] bg-[linear-gradient(135deg,#5b47a0,#a996e0)] text-[13px] font-semibold tracking-normal text-white",
  formHead: "mb-7 flex flex-col gap-3.5 max-[560px]:mb-6 max-[560px]:gap-2.5",
  formHeadTight: "mb-4 flex flex-col gap-2.5",
  title:
    "font-display text-[27px] font-light leading-[1.15] tracking-[-0.035em] text-spot-ink max-[560px]:text-[clamp(24px,6vw,27px)]",
  sub: "text-[15px] leading-[1.5] tracking-[-0.005em] text-spot-sub",
  form: "flex flex-col gap-6",
  fields: "flex flex-col gap-[18px]",
  row: "grid grid-cols-2 items-start gap-4 max-[560px]:grid-cols-1 max-[560px]:gap-[18px]",
  actions: "flex flex-col gap-3",
  alt: "flex flex-col gap-3.5",
  field: "flex min-w-0 flex-col gap-2.5",
  labelRow: "flex items-baseline justify-between gap-3",
  label: "text-[13.5px] leading-[1.2] tracking-[-0.005em] text-spot-body",
  link: `text-[13px] leading-[1.25] ${textLink}`,
  input: `${inputControl} max-[900px]:text-[16px]`,
  // Room on the right for the show/hide button.
  passwordInput: `${inputControl} pr-11 max-[900px]:text-[16px]`,
  primary: `${button} bg-spot-night tracking-[-0.01em] text-white after:bg-[linear-gradient(90deg,transparent,#ffffff2e,transparent)] hover:enabled:-translate-y-0.5 hover:enabled:bg-[#433369] hover:enabled:shadow-[0_10px_24px_-12px_#30234fb3] active:enabled:translate-y-0`,
  ghost: `${button} border border-spot-violet/15 bg-white/70 text-spot-night after:bg-[linear-gradient(90deg,transparent,#7c62d61f,transparent)] hover:enabled:-translate-y-px hover:enabled:border-spot-violet/30 hover:enabled:bg-white hover:enabled:shadow-[0_8px_18px_-12px_#3c346473] [&>svg]:hover:enabled:scale-110`,
  // Sign up fits three buttons in a row; narrow screens tighten them.
  tight: "max-[560px]:gap-1.5 max-[560px]:px-2",
  social: "grid grid-cols-2 gap-2.5",
  socialThree:
    "grid grid-cols-3 gap-2.5 max-[379px]:grid-cols-2 max-[379px]:[&>:last-child]:col-span-full",
  divider: "flex items-center gap-3",
  dividerLine:
    "h-px flex-1 animate-auth-divider bg-[linear-gradient(90deg,transparent,#5b47a040,transparent,#5b47a040,transparent)] bg-[length:200%_100%]",
  dividerText: "whitespace-nowrap text-[12px] leading-none text-spot-muted",
  foot: "flex flex-col gap-3.5",
  cta: "group flex items-center justify-between gap-3 rounded-xl border border-spot-violet/[.12] bg-[linear-gradient(135deg,#7c62d614,#7c62d605)] px-3.5 py-[11px] transition-colors duration-200 hover:border-spot-violet/30 hover:bg-[linear-gradient(135deg,#7c62d626,#7c62d60a)] max-[379px]:flex-col max-[379px]:items-start max-[379px]:gap-2",
  ctaText: "text-[13px] leading-[1.3] text-spot-sub",
  ctaAction:
    "inline-flex items-center gap-1.5 whitespace-nowrap text-[13px] font-medium leading-[1.3] text-spot-violet [&>svg]:size-3.5 [&>svg]:transition-transform [&>svg]:duration-200 [&>svg]:group-hover:translate-x-[3px]",
  note: `flex items-center justify-center gap-1.5 text-[12px] leading-[1.4] text-spot-muted max-[560px]:items-start [&>svg]:size-3.5 [&>svg]:shrink-0 max-[560px]:[&>svg]:mt-0.5 ${underlinedLinks}`,
};

// Content rises in on load; each element sets its delay with --d.
export const rise = "animate-auth-rise";

// Shared with the password pages.
export const fieldError = "text-[12px] leading-[1.5] text-overdue-strong";
export const passwordToggle = `${iconButton} absolute right-1 top-1/2 size-9 -translate-y-1/2`;

// Weak to strong, in the pastel palette: the filled segments take the
// colour of the current level. The word beside them carries the meaning,
// so it stays in body text colour.
const SEGMENTS = ["#e8835f", "#eda16c", "#e3bf66", "#94bf87", "#5f9d70"];

export const PasswordStrength = ({ password, className = "", ...props }) => {
  const score = password ? strengthIndicator(password) : 0;
  const filled = password ? Math.max(1, score) : 0;
  const color = SEGMENTS[filled - 1];
  return (
    <div
      className={`flex items-center gap-3 max-[379px]:grid max-[379px]:grid-cols-[minmax(0,1fr)_auto] max-[379px]:gap-2 ${className}`}
      aria-live="polite"
      {...props}
    >
      <span className="whitespace-nowrap text-[12px] leading-none text-spot-muted">
        Password strength
      </span>
      <div
        className="grid flex-1 grid-cols-5 gap-1 max-[379px]:col-span-full max-[379px]:row-start-2"
        aria-hidden="true"
      >
        {SEGMENTS.map((segment, index) => (
          <span
            key={segment}
            className="h-1 rounded-sm bg-spot-violet/10 transition-colors duration-[250ms]"
            style={index < filled ? { background: color } : undefined}
          />
        ))}
      </div>
      <span className="min-w-14 text-right text-[12px] font-medium leading-none text-spot-text">
        {password ? strengthColor(score).label : ""}
      </span>
    </div>
  );
};
