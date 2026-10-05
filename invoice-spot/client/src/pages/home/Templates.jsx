import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import Section from "./Section";

// The PDF templates: the same sample invoice drawn in each, side by side,
// each one a choice. Classic keeps to rules, Modern opens with a band of
// colour, and Compact fits more on a page.
const TEMPLATES = [
  { key: "classic", name: "Classic", caption: "Calm and familiar" },
  { key: "modern", name: "Modern", caption: "A bold band of colour" },
  { key: "compact", name: "Compact", caption: "More on one page" },
];

const LINES = [
  ["Brand identity", "30,000.00"],
  ["Website pages", "15,000.00"],
  ["Support, 5 hours", "5,000.00"],
];

// How each template spaces and rules the page.
const STYLE = {
  classic: { gap: "gap-4", row: "py-[5px] border-[#d9d6e6]" },
  modern: { gap: "gap-4", row: "py-[5px] border-spot-lilac" },
  compact: { gap: "gap-2.5", row: "py-[3px] border-[#e3e1ee]" },
};

const Heading = ({ template }) => {
  if (template === "modern")
    return (
      <div className="-mx-5 -mt-5 flex items-start justify-between gap-4 bg-spot-violet px-5 py-4 text-white">
        <span className="font-display text-[16px]">Tester Studio</span>
        <span className="text-right">
          <span className="block font-display text-[24px] font-light leading-none">
            Invoice
          </span>
          <span className="text-[11px] text-white/80">INV-0042</span>
        </span>
      </div>
    );
  if (template === "compact")
    return (
      <div className="flex justify-between gap-4 text-[12px] text-spot-ink">
        <span className="font-medium">Tester Studio</span>
        <span>Invoice · INV-0042</span>
      </div>
    );
  return (
    <div className="flex items-start justify-between gap-4 border-b border-[#d9d6e6] pb-2.5">
      <span className="font-display text-[16px] text-spot-ink">
        Tester Studio
      </span>
      <span className="text-right">
        <span className="block font-display text-[18px] leading-none text-spot-ink">
          Invoice
        </span>
        <span className="text-[11px] text-spot-faint">INV-0042</span>
      </span>
    </div>
  );
};

// The sample invoice in a template. Side by side the three share a page's
// proportions; stacked on a phone, each is as tall as its content. It is a
// picture of the PDF; the caption under it names the template.
const Mock = ({ template }) => (
  <div
    className={`flex w-full flex-col overflow-hidden rounded-xl border border-[#2327350f] bg-white p-5 shadow-[0_30px_50px_-30px_#30234f80] min-[900px]:aspect-[210/270] ${STYLE[template].gap}`}
    data-template={template}
    aria-hidden="true"
  >
    <Heading template={template} />
    <div className="flex justify-between gap-4 text-[11.5px] leading-[1.5] text-spot-muted">
      <span>
        Bill to
        <br />
        <span className="text-spot-ink">Harbor &amp; Pine Architects</span>
      </span>
      <span className="text-right">
        Due
        <br />
        <span className="text-spot-ink">Oct 18, 2026</span>
      </span>
    </div>
    <div>
      {LINES.map(([item, amount]) => (
        <div
          className={`flex justify-between gap-4 border-b text-[12px] text-spot-text ${STYLE[template].row}`}
          key={item}
        >
          <span>{item}</span>
          <span>{amount}</span>
        </div>
      ))}
    </div>
    <div className="mt-auto flex items-end justify-between gap-4">
      <span className="text-[11.5px] leading-[1.5] text-spot-muted">
        CGST 9% 4,500.00
        <br />
        SGST 9% 4,500.00
      </span>
      <span className="font-display text-[20px] font-light text-spot-ink">
        ₹ 59,000.00
      </span>
    </div>
  </div>
);

// One template to choose: the drawing and its caption around a hidden
// radio, so the card shows the radio's checked and keyboard focus states.
const choice =
  "group flex cursor-pointer select-none flex-col gap-3 rounded-[20px] border border-transparent p-2.5 transition-[border-color,background-color,box-shadow] has-[:checked]:border-spot-night has-[:checked]:bg-white/60 has-[:checked]:ring-[3px] has-[:checked]:ring-spot-night/15 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-[3px] has-[:focus-visible]:outline-[#5b47a0] [&:not(:has(:checked)):hover]:bg-white/40";

const Templates = ({ motion }) => {
  const [template, setTemplate] = useState("classic");
  return (
    <Section
      id="templates"
      title="PDFs that look like your business."
      line="Pick a template once; every invoice, quotation, receipt and statement follows it."
      motion={motion}
    >
      <fieldset className="m-0 min-w-0 border-0 p-0">
        <legend className="sr-only">Template</legend>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,280px),1fr))] gap-7 max-[899px]:gap-5">
          {TEMPLATES.map(({ key, name, caption }) => (
            <label className={choice} key={key}>
              <input
                className="sr-only"
                type="radio"
                name="template"
                value={key}
                checked={template === key}
                onChange={() => setTemplate(key)}
              />
              <Mock template={key} />
              <span className="flex items-center gap-2.5 px-1.5 pb-1">
                <span className="grid size-5 shrink-0 place-items-center rounded-full border border-spot-faint text-transparent group-has-[:checked]:border-spot-night group-has-[:checked]:bg-spot-night group-has-[:checked]:text-white [&>svg]:size-3 [&>svg]:stroke-[2.5]">
                  <FiCheck aria-hidden="true" />
                </span>
                <span className="text-[14.5px] font-medium text-spot-ink">
                  {name}
                </span>
                <span className="ml-auto text-right text-[13px] text-spot-muted">
                  {caption}
                </span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
    </Section>
  );
};

export default Templates;
