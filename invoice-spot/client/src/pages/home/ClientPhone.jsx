import QrPattern from "./QrPattern";
import { dayMonthYear } from "../../utils/dates";

// Two weeks after the visitor's own today, so the sample is always still to
// pay: a homepage has no business, and so no business's today.
const dueDay = () => {
  const day = new Date();
  day.setHours(0, 0, 0, 0);
  day.setDate(day.getDate() + 14);
  return dayMonthYear(day);
};

// What the client sees on their phone: the invoice's number, due date and
// amount, the sample UPI QR, and the two things they can do with it. It
// overlaps the tour's corner and floats gently; a phone-sized window has
// no room for it.
const ClientPhone = ({ motion }) => (
  <div
    className={`absolute -bottom-16 -left-[72px] w-[196px] rounded-[34px] bg-[#0e0a1a] p-[9px] shadow-[0_40px_80px_-30px_#000000cc,0_0_0_1px_#ffffff1f] max-[860px]:hidden ${motion ? "animate-home-float" : ""}`}
    role="img"
    aria-label="What your client sees on their phone: the invoice, a UPI QR, Open in UPI app and Report a payment."
  >
    <div className="overflow-hidden rounded-[26px] bg-white text-spot-ink">
      <div className="bg-spot-thead px-3.5 pb-3 pt-4">
        <p className="text-[11px] text-spot-muted">Tester Studio</p>
        <p className="mt-0.5 font-display text-[18px] font-light tracking-[-0.03em]">
          INV-0042
        </p>
        <p className="mt-0.5 text-[11px] text-spot-muted">Due {dueDay()}</p>
      </div>
      <div className="flex flex-col items-center gap-[9px] px-3.5 pb-4 pt-3">
        <p className="text-[11px] text-spot-muted">Amount due</p>
        <p className="-mt-1 font-display text-[21px] font-light tracking-[-0.03em]">
          ₹ 59,000.00
        </p>
        <QrPattern className="size-24" />
        <span className="flex min-h-[34px] w-full items-center justify-center rounded-[11px] bg-spot-night text-[12px] font-medium text-white">
          Open in UPI app
        </span>
        <span className="text-[11.5px] font-medium text-spot-violet">
          Report a payment
        </span>
      </div>
    </div>
  </div>
);

export default ClientPhone;
