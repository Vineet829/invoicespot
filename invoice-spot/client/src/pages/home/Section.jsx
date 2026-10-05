import { useReveal } from "./motion";

// The homepage's section headings and the line under each.
export const heading =
  "text-balance font-display text-[clamp(30px,4vw,46px)] font-extralight leading-[1.08] tracking-[-0.05em] text-spot-ink";
export const lead = "text-pretty text-[17px] leading-[1.6] text-spot-body";

// A section of the page, named by its heading, that rises in as it comes
// into view. Its id is where the header's links land; `top` is its room
// above, a padding class.
const Section = ({ id, title, line, motion, top = "pt-12", children }) => {
  const [ref, reveal] = useReveal(motion);
  return (
    <section
      id={id}
      ref={ref}
      className={`flex flex-col gap-7 pb-6 ${top} max-[700px]:gap-6 max-[700px]:pt-9 ${reveal}`}
      aria-labelledby={`${id}-title`}
    >
      <div className="flex max-w-[720px] flex-col gap-3.5">
        <h2 id={`${id}-title`} className={heading}>
          {title}
        </h2>
        <p className={lead}>{line}</p>
      </div>
      {children}
    </section>
  );
};

export default Section;
