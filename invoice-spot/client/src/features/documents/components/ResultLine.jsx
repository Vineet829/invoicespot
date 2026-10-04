import { textLink } from "../../../components/workspace/pastel";

// The line above a filtered list: what the filters found and what it adds
// up to, then a way back to the whole list. Each part may hold a figure
// that stands out; an overdue part takes the overdue colour. A part keeps to
// one line and starts with its dot, so a wrapped line never ends on one.
// The line dims with the list while the next figures load.
const ResultLine = ({ parts, onClear, busy = false }) => (
  <div
    className={`mb-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 rounded-[14px] bg-spot-brand/[.08] px-3.5 py-2.5 text-[13.5px] text-spot-text transition-opacity ${busy ? "opacity-60" : ""}`}
  >
    <p className="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1">
      {parts.map((part, index) => (
        <span
          key={index}
          className={`whitespace-nowrap max-[700px]:whitespace-normal ${part.tone === "overdue" ? "text-overdue-strong" : ""}`}
        >
          {index > 0 && (
            <>
              <span className="sr-only">, </span>
              <span className="mr-2.5 text-spot-disabled" aria-hidden="true">
                ·
              </span>
            </>
          )}
          {part.before}
          {part.strong && (
            <strong className="font-medium">{part.strong}</strong>
          )}
          {part.after}
        </span>
      ))}
    </p>
    <button
      type="button"
      className={`ml-auto ${textLink} bg-transparent p-0 text-[13px] coarse:min-h-11`}
      onClick={onClear}
    >
      Clear filters
    </button>
  </div>
);

export default ResultLine;
