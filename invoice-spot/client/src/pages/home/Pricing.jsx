import { Link } from "react-router-dom";
import { column } from "./layout";
import { useReveal } from "./motion";

// The three questions a visitor asks before signing up.
const QUESTIONS = [
  [
    "What's included?",
    "Invoices, quotations and recurring invoices, UPI and bank payments, reminders, the client portal, reports and team roles, in over 150 currencies.",
  ],
  [
    "Do I need a GSTIN?",
    "No. Without one, invoices use simple tax rates and no GST terms.",
  ],
  [
    "Is my data safe?",
    "Two-factor sign-in, links you can revoke, and a full export whenever you want it.",
  ],
];

// The price, which is none, with sign-up, beside the three questions.
const Pricing = ({ motion }) => {
  const [ref, reveal] = useReveal(motion);
  return (
    <section
      id="pricing"
      ref={ref}
      className={`${column} flex flex-wrap items-start gap-[clamp(36px,5vw,72px)] pt-[clamp(80px,9vw,120px)] ${reveal}`}
      aria-labelledby="pricing-title"
    >
      <div className="min-w-0 flex-[1_1_360px]">
        <h2
          id="pricing-title"
          className="font-display text-[clamp(56px,6vw,84px)] font-extralight leading-none tracking-[-0.05em] text-spot-ink [&>span]:text-spot-brand"
        >
          Free<span>.</span>
        </h2>
        <p className="mt-[18px] max-w-[400px] text-[18px] leading-[1.6] text-spot-body">
          Everything included, for you and your team. No card needed.
        </p>
        <p className="mt-7">
          <Link
            to="/register"
            className="inline-flex min-h-[52px] items-center rounded-[15px] bg-spot-night px-[26px] text-[16px] font-medium text-white transition-colors hover:bg-[#433369]"
          >
            Start for free
          </Link>
        </p>
      </div>
      <dl className="min-w-0 flex-[1.1_1_440px]">
        {QUESTIONS.map(([question, answer], index) => (
          <div
            key={question}
            className={`py-5 first:pt-0 last:pb-0 ${index ? "border-t border-[#2327351a]" : ""}`}
          >
            <dt className="text-[17px] font-medium text-spot-ink">
              {question}
            </dt>
            <dd className="mt-1.5 text-[15.5px] leading-[1.6] text-spot-body">
              {answer}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
};

export default Pricing;
