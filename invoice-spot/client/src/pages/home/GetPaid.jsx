import { useEffect, useState } from "react";
import { FiSend } from "react-icons/fi";
import QrPattern from "./QrPattern";
import { column } from "./layout";
import { useReveal, useRunning } from "./motion";

// How an invoice gets paid, in three steps.
const STEPS = [
  ["Send", "A GST tax invoice with a UPI QR for the balance."],
  ["They pay", "By UPI or bank, and they tell you from the invoice."],
  ["You confirm", "One click, and the invoice is paid in full."],
];
const TURN = 2800;

// The sample invoice's lines.
const LINES = [
  ["Brand identity design", "HSN 998391", "40,000.00"],
  ["Website design, 5 pages", "HSN 998314", "10,000.00"],
];

// The PDF templates, and how each dresses the sample invoice: Classic keeps
// to rules, Modern opens with a band of colour, and Compact fits more on a
// page.
const paper =
  "relative bg-white text-spot-ink shadow-[0_50px_100px_-55px_#28225080]";
const head = "flex flex-wrap justify-between";
const TEMPLATES = [
  {
    key: "classic",
    label: "Classic",
    invoice: `${paper} rounded-[20px] px-7 py-[26px] text-[14px]`,
    head: `${head} gap-3 pb-3.5`,
    headLabel: "text-spot-muted",
    title: "text-[24px]",
    block: "pb-3.5",
    row: "py-[11px]",
  },
  {
    key: "modern",
    label: "Modern",
    invoice: `${paper} rounded-[20px] px-7 py-[26px] text-[14px]`,
    head: `${head} -mx-7 -mt-[26px] mb-4 gap-3 rounded-t-[20px] bg-spot-violet px-7 py-[22px] text-white`,
    headLabel: "text-card-lilac",
    title: "text-[28px]",
    block: "pb-3.5",
    row: "py-[11px]",
  },
  {
    key: "compact",
    label: "Compact",
    invoice: `${paper} rounded-2xl px-5 py-[18px] text-[12.8px]`,
    head: `${head} gap-2.5 pb-2`,
    headLabel: "text-spot-muted",
    title: "text-[19px]",
    block: "pb-2",
    row: "py-1.5",
  },
];

const templateButton =
  "min-h-8 bg-transparent p-0 underline-offset-[6px] transition-colors";
const chosen = "font-medium text-spot-night underline decoration-2";

// The sample invoice in a template, with the step's overlay over it: the
// pill that says it was sent, the client's reported payment to confirm, or
// the stamp. The overlays are pictures; the steps beside the invoice say
// what they show.
const SampleInvoice = ({ look, step, motion }) => (
  <div className={look.invoice} data-template={look.key}>
    <div className={look.head}>
      <div>
        <p className={`text-[11.5px] tracking-[.14em] ${look.headLabel}`}>
          TAX INVOICE
        </p>
        <p
          className={`mt-[3px] font-display font-light tracking-[-0.03em] ${look.title}`}
        >
          INV-0042
        </p>
      </div>
      <div className="text-right">
        <p className="font-medium">Tester Studio</p>
        <p className={`mt-[3px] ${look.headLabel}`}>GSTIN 27ABCDE1234F1Z5</p>
      </div>
    </div>
    <p className={`text-spot-muted ${look.block}`}>
      Bill to{" "}
      <span className="font-medium text-spot-ink">
        Harbor &amp; Pine Architects
      </span>{" "}
      · Maharashtra (27)
    </p>
    <div
      role="table"
      aria-label="Invoice lines"
      className="border-t border-[#23273514]"
    >
      {LINES.map(([item, code, amount], index) => (
        <div
          role="row"
          key={item}
          className={`grid grid-cols-[minmax(0,3fr)_minmax(0,1.1fr)_minmax(0,1.3fr)] gap-2 ${look.row} ${index ? "border-t border-[#2327350f]" : ""}`}
        >
          <span role="cell">{item}</span>
          <span role="cell" className="text-spot-muted">
            {code}
          </span>
          <span role="cell" className="text-right">
            {amount}
          </span>
        </div>
      ))}
    </div>
    <div className="flex items-end justify-between gap-4 border-t border-[#23273514] pt-3.5">
      <div className="flex items-center gap-3">
        <span role="img" aria-label="A sample UPI QR pattern">
          <QrPattern className="size-[58px]" />
        </span>
        <p className="text-[0.93em] leading-[1.5] text-spot-muted">
          Pay by UPI
          <br />
          yourstudio@upi
        </p>
      </div>
      <div className="text-right">
        <p className="text-spot-muted">CGST 9% + SGST 9% · 9,000.00</p>
        <p className="mt-1 text-[1.3em] font-medium">₹ 59,000.00</p>
        <p className="mt-[3px] text-[0.9em] text-spot-muted">
          {step === 2 ? "Balance due 0.00" : "Rupees Fifty-Nine Thousand Only"}
        </p>
      </div>
    </div>
    {step === 0 && (
      <span
        className={`absolute -top-[15px] left-1/2 -ml-[118px] inline-flex w-[236px] items-center justify-center gap-2 rounded-full bg-spot-night px-3.5 py-2 text-[13px] font-medium text-white shadow-[0_16px_30px_-16px_#30234fcc] [&>svg]:size-[15px] ${motion ? "animate-home-pop" : ""}`}
        aria-hidden="true"
      >
        <FiSend />
        Sent by email and WhatsApp
      </span>
    )}
    {step === 1 && (
      <div
        className={`absolute bottom-4 left-4 right-4 flex flex-wrap items-center justify-between gap-2.5 rounded-2xl bg-white px-4 py-3.5 shadow-[0_24px_48px_-20px_#2822508c,0_0_0_1px_#2327350f] ${motion ? "animate-home-pop" : ""}`}
        aria-hidden="true"
      >
        <span className="text-[14px]">
          <span className="block text-[12.5px] text-spot-muted">
            Payment reported · just now
          </span>
          <span className="font-medium">
            Harbor &amp; Pine paid ₹ 59,000.00 by UPI
          </span>
        </span>
        <span className="inline-flex min-h-9 items-center rounded-[11px] bg-spot-night px-3.5 text-[13.5px] font-medium text-white">
          Confirm
        </span>
      </div>
    )}
    {step === 2 && (
      <span
        className={`absolute left-[55%] top-[46%] -translate-x-1/2 -translate-y-1/2 -rotate-12 rounded-xl border-[3px] border-[#3f7f3a] bg-white/[.86] px-[18px] py-2 font-display text-[22px] font-medium tracking-[.12em] text-[#3f7f3a] ${motion ? "animate-home-stamp" : ""}`}
        aria-hidden="true"
      >
        PAID IN FULL
      </span>
    )}
  </div>
);

// From invoice to paid: the three steps, one at a time, beside the sample
// invoice that shows each, in the PDF template of the visitor's choice. The
// steps move on by themselves while the section is on screen in a tab
// being looked at; a click picks one, which then has its full turn. Every
// step keeps its full colour; the one playing has a filled number and a
// bar under its line that runs out with its turn.
const GetPaid = ({ motion }) => {
  const [ref, reveal] = useReveal(motion);
  const running = useRunning(ref, motion);
  const [step, setStep] = useState(0);
  // Counted so that a click on the step already shown starts its turn,
  // and its bar, over as well.
  const [picks, setPicks] = useState(0);
  const [template, setTemplate] = useState("classic");
  // Each step has its turn, from when it appeared or was picked.
  useEffect(() => {
    if (!running) return undefined;
    const id = setTimeout(
      () => setStep((current) => (current + 1) % STEPS.length),
      TURN,
    );
    return () => clearTimeout(id);
  }, [running, step, picks]);
  const pick = (index) => {
    setStep(index);
    setPicks((count) => count + 1);
  };
  return (
    <section
      id="get-paid"
      ref={ref}
      className={`${column} flex flex-wrap items-center gap-[clamp(36px,5vw,72px)] pt-[clamp(80px,9vw,120px)] ${reveal}`}
      aria-labelledby="paid-title"
    >
      <div className="min-w-0 flex-[1_1_360px]">
        <h2
          id="paid-title"
          className="font-display text-[clamp(32px,3.4vw,46px)] font-light leading-[1.08] tracking-[-0.045em] text-spot-ink"
        >
          GST invoices your clients can pay in a tap.
        </h2>
        <ol className="mb-0 mt-[34px] flex list-none flex-col gap-[22px] p-0">
          {STEPS.map(([title, line], index) => {
            const active = index === step;
            return (
              <li key={title}>
                <button
                  type="button"
                  className="group flex items-start gap-4 bg-transparent p-0 text-left text-spot-ink"
                  onClick={() => pick(index)}
                  aria-current={active ? "step" : undefined}
                >
                  <span
                    className={`grid size-[34px] shrink-0 place-items-center rounded-full text-[14px] transition-[background-color,border-color,box-shadow] ${active ? "bg-spot-night text-white shadow-[0_0_0_4px_#7c62d62e]" : "border-[1.5px] border-spot-night/30 text-spot-body group-hover:border-spot-night/60"}`}
                  >
                    {index + 1}
                  </span>
                  <span className="relative">
                    <span className="block text-[18px] font-medium">
                      {title}
                    </span>
                    <span className="mt-1 block text-[15.5px] leading-[1.55] text-spot-body">
                      {line}
                    </span>
                    {/* The step's turn, running out under its line. It starts
                        over with the step, and with a click. */}
                    {active && (
                      <span
                        className="absolute inset-x-0 -bottom-2.5 h-0.5 overflow-hidden rounded-full bg-spot-night/10"
                        aria-hidden="true"
                      >
                        <span
                          key={`${picks}-${running}`}
                          className={`block h-full origin-left rounded-full bg-spot-brand ${motion ? "animate-home-progress" : ""}`}
                        />
                      </span>
                    )}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </div>
      <div className="min-w-0 flex-[1.1_1_440px]">
        <div
          role="group"
          aria-label="PDF template"
          className="mb-[26px] flex justify-end gap-[18px] text-[14px]"
        >
          {TEMPLATES.map(({ key, label }) => (
            <button
              type="button"
              key={key}
              className={`${templateButton} ${key === template ? chosen : "text-spot-muted"}`}
              aria-pressed={key === template}
              onClick={() => setTemplate(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <SampleInvoice
          look={TEMPLATES.find(({ key }) => key === template)}
          step={step}
          motion={motion}
        />
        <p className="mt-2.5 text-right text-[12.5px] text-spot-muted">
          Sample invoice
        </p>
      </div>
    </section>
  );
};

export default GetPaid;
