import { column } from "./layout";
import { useReveal } from "./motion";

// What the app covers, in four facts. The currency list has 179 codes,
// but some are metals, funds, test codes or retired currencies; over 150
// are money a client can be billed in.
const FACTS = [
  ["150+", "currencies to invoice in"],
  ["GST-ready", "CGST, SGST or IGST by place of supply"],
  ["4 roles", "for your team, owner to viewer"],
  ["1 step", "to confirm a payment"],
];

// A strip of facts under the hero, each on a thin rule, that rises in as
// it comes into view.
const Facts = ({ motion }) => {
  const [ref, reveal] = useReveal(motion);
  return (
    <section
      ref={ref}
      className={`${column} pt-[clamp(40px,5vw,64px)] ${reveal}`}
      aria-label="In numbers"
    >
      <dl className="grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] gap-7">
        {FACTS.map(([figure, line]) => (
          <div className="border-l border-[#2327351f] pl-5" key={figure}>
            <dt className="font-display text-[clamp(34px,3.4vw,46px)] font-extralight tracking-[-0.05em] text-spot-ink">
              {figure}
            </dt>
            <dd className="mt-1.5 text-[15px] text-spot-body">{line}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
};

export default Facts;
