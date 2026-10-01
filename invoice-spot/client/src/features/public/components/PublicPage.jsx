import { FiLink } from "react-icons/fi";
import { appSurface, retryButton } from "../../../components/workspace/pastel";
import { cardBase } from "./PublicDocument";

// The page a client's link opens: no workspace around it, centred on the
// canvas.
export const frame = `${appSurface} flex min-h-dvh flex-col items-center gap-6 px-8 py-10 max-[700px]:px-4 max-[700px]:py-6`;

// A message in place of the document, such as for a link that was turned
// off. The link holds no account, so there is nowhere else to send people.
export const Notice = ({ title, text, retry }) => (
  <div className={`${cardBase} max-w-[520px]`}>
    <span className="mb-6 grid size-14 place-items-center rounded-[18px] bg-white/80 text-spot-violet shadow-key [&>svg]:size-6 [&>svg]:stroke-[1.5]">
      <FiLink aria-hidden="true" />
    </span>
    <h1 className="font-display text-[32px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink [&>span]:text-spot-brand">
      {title}
      <span>.</span>
    </h1>
    <p className="mt-3 text-[14.5px] leading-[1.6] text-spot-sub">{text}</p>
    {retry && (
      <button type="button" className={`${retryButton} mt-6`} onClick={retry}>
        Try again
      </button>
    )}
  </div>
);
