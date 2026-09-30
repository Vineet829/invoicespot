import { appSurface, surface } from "../workspace/pastel";
import PublicHeader from "./PublicHeader";

export const PublicFooter = () => (
  <footer className="flex flex-wrap items-baseline justify-between gap-x-8 gap-y-1 border-t border-[#23273514] pt-5 text-[13px] text-spot-muted">
    <span>
      © {new Date().getFullYear()}{" "}
      <strong className="font-medium text-spot-text">Invoice Spot</strong>
    </span>
    <span>
      Made for{" "}
      <strong className="font-medium text-spot-text">
        independent business
      </strong>
      <span className="text-spot-brand">.</span>
    </span>
  </footer>
);

// Public pages share the workspace's canvas: header, content, footer.
const pageFrame = `${appSurface} flex min-h-dvh flex-col gap-6 px-8 pb-6 pt-4 compact:gap-4 max-[960px]:px-6 max-[700px]:px-4 max-[700px]:pb-5 max-[700px]:pt-2`;

// A single task on its own, such as resetting a password: one card in the
// middle of the canvas.
const PublicPage = ({ children }) => (
  <div className={pageFrame}>
    <PublicHeader />
    <main
      className="flex flex-1 items-center justify-center py-6 max-[700px]:items-start max-[700px]:py-2"
      id="main"
    >
      <div
        className={`${surface} w-full max-w-[480px] animate-ov-rise p-8 max-[700px]:rounded-3xl max-[700px]:p-6`}
      >
        {children}
      </div>
    </main>
    <PublicFooter />
  </div>
);

// The top of a public card: an icon, a title with the brand's full stop and
// a short explanation.
export const CardHeading = ({ icon: Icon, title, children }) => (
  <>
    <span className="mb-6 grid size-14 place-items-center rounded-[18px] bg-white/80 text-spot-violet shadow-key [&>svg]:size-6 [&>svg]:stroke-[1.5]">
      <Icon aria-hidden="true" />
    </span>
    <h1 className="font-display text-[34px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[30px] [&>span]:text-spot-brand">
      {title}
      <span>.</span>
    </h1>
    {children && (
      <p className="mt-3 text-[14.5px] leading-[1.6] text-spot-sub">
        {children}
      </p>
    )}
  </>
);

export default PublicPage;
