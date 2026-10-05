import {
  FiBell,
  FiCreditCard,
  FiFileText,
  FiGlobe,
  FiLayers,
  FiMinusCircle,
  FiMonitor,
  FiPercent,
  FiRepeat,
  FiShield,
  FiSmartphone,
  FiUsers,
} from "react-icons/fi";
import QrPattern from "./QrPattern";
import Section from "./Section";

// What the app does, as cards in the financial cards' colours: the two
// about tax and getting paid are large, with a glimpse of each, and the
// rest smaller.
const TONES = {
  blue: { card: "bg-card-blue", icon: "text-card-blue-icon" },
  lilac: { card: "bg-card-lilac", icon: "text-avatar-lilac-ink" },
  peach: { card: "bg-card-peach", icon: "text-card-peach-icon" },
  sage: { card: "bg-card-sage", icon: "text-card-sage-icon" },
};

const FEATURES = [
  {
    icon: FiPercent,
    tone: "blue",
    title: "GST, done right",
    text: "Turn on GST mode with your GSTIN: CGST and SGST within your state, IGST across states, HSN/SAC codes and GSTR-1 and 3B helper files.",
  },
  {
    icon: FiSmartphone,
    tone: "lilac",
    title: "Get paid by UPI or bank",
    text: "Every INR invoice carries a UPI QR code for what's due and your bank details. Clients report a payment; you confirm it.",
  },
  {
    icon: FiMonitor,
    tone: "peach",
    title: "A portal for every client",
    text: "One private link with their documents, balances and statement, where they can accept quotations and report payments.",
  },
  {
    icon: FiUsers,
    tone: "sage",
    title: "Work as a team",
    text: "Invite people as admins, members or viewers. Everyone sees who did what.",
  },
  {
    icon: FiRepeat,
    tone: "lilac",
    title: "Recurring invoices",
    text: "Weekly to yearly. Issue each one for you, or leave a draft to check.",
  },
  {
    icon: FiCreditCard,
    tone: "blue",
    title: "Expenses and profit",
    text: "Record what you spend and see profit by month.",
  },
  {
    icon: FiMinusCircle,
    tone: "peach",
    title: "Credit notes, refunds and TDS",
    text: "Every adjustment counts toward what's still owed.",
  },
  {
    icon: FiGlobe,
    tone: "sage",
    title: "Any currency",
    text: "Bill in any currency, with the exchange rate you set.",
  },
  {
    icon: FiLayers,
    tone: "lilac",
    title: "Your look",
    text: "Classic, Modern or Compact PDFs, with your logo.",
  },
  {
    icon: FiBell,
    tone: "blue",
    title: "Reminders on their own",
    text: "Polite reminders before and after the due date, sent for you.",
  },
  {
    icon: FiShield,
    tone: "peach",
    title: "Safe sign-in",
    text: "Two-factor codes, Google sign-in, and sessions you can see and end.",
  },
  {
    icon: FiFileText,
    tone: "sage",
    title: "Drafts, then issue",
    text: "Numbers stay in order and issued invoices stay as sent.",
  },
];

// The tax breakdown of a sample invoice, as the PDF prints it.
const GstTable = () => (
  <div
    className="mt-auto rounded-2xl bg-white/70 px-4 pb-3 pt-1.5 text-card-blue-ink"
    aria-hidden="true"
  >
    {[
      ["Taxable value", "50,000.00"],
      ["CGST 9%", "4,500.00"],
      ["SGST 9%", "4,500.00"],
    ].map(([label, amount]) => (
      <div
        className="flex justify-between gap-4 border-t border-[#2337501a] py-[7px] text-[14px] first:border-t-0"
        key={label}
      >
        <span>{label}</span>
        <span>{amount}</span>
      </div>
    ))}
    <div className="flex justify-between gap-4 border-t border-[#23375040] pb-0.5 pt-[9px] font-display text-[20px] font-light">
      <span>Total</span>
      <span>₹ 59,000.00</span>
    </div>
    <p className="mt-1.5 text-[12.5px] text-card-blue-muted">
      Rupees Fifty-Nine Thousand Only
    </p>
  </div>
);

// How a sample invoice asks to be paid. The placeholders are the ones the
// app shows until a business fills in its own.
const PayVisual = () => (
  <div className="mt-auto flex items-center gap-4 rounded-2xl bg-white/70 p-3.5 max-[419px]:flex-col max-[419px]:items-start">
    <QrPattern seed={11} className="size-24" />
    <div className="flex min-w-0 flex-col items-start gap-1.5 text-card-lilac-label">
      <span className="max-w-full truncate rounded-full bg-white/80 px-2.5 py-1 text-[12.5px]">
        UPI · yourstudio@upi
      </span>
      <span className="max-w-full truncate rounded-full bg-white/80 px-2.5 py-1 text-[12.5px]">
        Bank · IFSC ABCD0123456
      </span>
      <span className="mt-1 font-display text-[20px] font-light tracking-[-0.02em] text-card-lilac-ink">
        ₹ 59,000.00 due
      </span>
    </div>
  </div>
);

const Feature = ({
  icon: Icon,
  tone,
  title,
  text,
  large = false,
  children,
}) => (
  <article
    className={`flex min-w-0 flex-col gap-3 rounded-3xl ${large ? "p-7 max-[700px]:p-6" : "p-[22px]"} ${TONES[tone].card}`}
  >
    <span
      className={`grid size-[42px] place-items-center rounded-full bg-white/70 [&>svg]:size-[19px] [&>svg]:stroke-[1.6] ${TONES[tone].icon}`}
    >
      <Icon aria-hidden="true" />
    </span>
    <h3
      className={`font-display font-light leading-[1.15] tracking-[-0.04em] text-spot-ink ${large ? "text-[28px] max-[700px]:text-[25px]" : "text-[20px]"}`}
    >
      {title}
    </h3>
    <p
      className={`text-pretty leading-[1.55] text-spot-text ${large ? "text-[15.5px]" : "text-[14px]"}`}
    >
      {text}
    </p>
    {children}
  </article>
);

const Features = ({ motion }) => (
  <Section
    id="features"
    title="Everything a growing business sends, tracks and gets paid for."
    line="From the first quotation to the last receipt, with the tax, the reminders and the team around it."
    motion={motion}
    top="pt-10"
  >
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,460px),1fr))] gap-4">
      <Feature {...FEATURES[0]} large>
        <GstTable />
      </Feature>
      <Feature {...FEATURES[1]} large>
        <PayVisual />
      </Feature>
    </div>
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,260px),1fr))] gap-4">
      {FEATURES.slice(2).map((feature) => (
        <Feature {...feature} key={feature.title} />
      ))}
    </div>
  </Section>
);

export default Features;
