import {
  FiArrowRight,
  FiCheckCircle,
  FiSend,
  FiSmartphone,
} from "react-icons/fi";
import { surface } from "../../components/workspace/pastel";
import Section from "./Section";

// How an invoice gets paid, without a payment gateway in between.
const STEPS = [
  {
    icon: FiSend,
    title: "Issue the invoice",
    text: "A PDF with your logo, tax, bank details and a UPI QR for what's due.",
  },
  {
    icon: FiArrowRight,
    title: "Share it",
    text: "By email or WhatsApp, with a link your client can open anywhere.",
  },
  {
    icon: FiSmartphone,
    title: "Your client pays",
    text: "By UPI or bank transfer, then reports it from the same link.",
  },
  {
    icon: FiCheckCircle,
    title: "You confirm",
    text: "The invoice settles, the receipt is ready, and your overview updates.",
  },
];

const Steps = ({ motion }) => (
  <Section
    id="get-paid"
    title="Getting paid, in four steps."
    line="No payment gateway, no fees taken from your money. Your client pays you directly."
    motion={motion}
  >
    <ol className="m-0 grid list-none grid-cols-[repeat(auto-fit,minmax(min(100%,240px),1fr))] gap-4 p-0">
      {STEPS.map(({ icon: Icon, title, text }, index) => (
        <li
          className={`${surface} flex min-w-0 flex-col gap-3 p-[22px]`}
          key={title}
        >
          <span className="flex items-center justify-between">
            <span className="grid size-[42px] place-items-center rounded-[14px] bg-card-lilac text-avatar-lilac-ink [&>svg]:size-[19px] [&>svg]:stroke-[1.6]">
              <Icon aria-hidden="true" />
            </span>
            <span
              className="font-display text-[34px] font-extralight leading-none text-spot-lilac"
              aria-hidden="true"
            >
              {index + 1}
            </span>
          </span>
          <h3 className="font-display text-[20px] font-light tracking-[-0.035em] text-spot-ink">
            {title}
          </h3>
          <p className="text-pretty text-[14px] leading-[1.55] text-spot-body">
            {text}
          </p>
        </li>
      ))}
    </ol>
  </Section>
);

export default Steps;
