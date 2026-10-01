import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { FiCopy, FiExternalLink, FiSmartphone } from "react-icons/fi";
import { toast } from "react-toastify";
import { money } from "../../../components/workspace/Workspace";
import {
  primaryButton,
  rowAction,
  secondaryButton,
} from "../../../components/workspace/pastel";

const copy = (label, value) => async () => {
  try {
    await navigator.clipboard.writeText(value);
    toast.success(`${label} copied.`);
  } catch {
    toast.error(`The ${label} couldn’t be copied. Select it instead.`);
  }
};

// A QR code any UPI app can scan to pay the balance, black on white as
// scanners read best, and on a phone a link that opens the UPI app with the
// payment filled in.
const UpiCode = ({ upi }) => {
  const [image, setImage] = useState(null);
  useEffect(() => {
    let current = true;
    QRCode.toDataURL(upi.uri, {
      errorCorrectionLevel: "M",
      margin: 4,
      width: 232,
    }).then(
      (url) => current && setImage(url),
      () => current && setImage(null),
    );
    return () => {
      current = false;
    };
  }, [upi.uri]);
  return (
    <figure className="m-0 flex w-[232px] shrink-0 flex-col items-center gap-3 max-[560px]:w-full">
      {image && (
        <img
          src={image}
          width="232"
          height="232"
          alt="UPI QR code to pay this invoice"
          className="rounded-xl"
        />
      )}
      <figcaption className="text-center text-[12.5px] leading-[1.5] text-spot-muted">
        {upi.amountIncluded
          ? `Scan to pay ${money(upi.amount, "INR")} with any UPI app.`
          : "Scan with any UPI app and enter the amount. UPI limits apply."}
      </figcaption>
      <a
        href={upi.uri}
        className={`${secondaryButton} hidden w-full coarse:inline-flex`}
      >
        <FiSmartphone aria-hidden="true" /> Open in UPI app
      </a>
    </figure>
  );
};

// How to pay an unpaid invoice, with whichever details the business gave:
// its bank account, UPI ID, a link to pay online and instructions, and for
// rupees a UPI QR code. Account numbers, codes and IDs can be copied as they
// are.
const PaymentDetails = ({ payment }) => {
  const rows = [
    ["Account holder", payment.accountHolder],
    ["Bank", payment.bankName],
    ["Account number", payment.accountNumber, true],
    [payment.bankCodeLabel, payment.bankCode, true],
    ["UPI ID", payment.upiId, true],
  ].filter(([, value]) => value);
  return (
    <section
      className="mt-9 border-t border-[#23273512] pt-7"
      aria-labelledby="how-to-pay"
    >
      <h2
        id="how-to-pay"
        className="mb-3 text-[12.5px] font-normal text-spot-muted"
      >
        How to pay
      </h2>
      <div className="flex flex-wrap items-start gap-x-10 gap-y-7">
        <div className="min-w-0 flex-1 basis-[320px]">
          {rows.length > 0 && (
            <dl className="grid max-w-[620px] grid-cols-[auto_minmax(0,1fr)] items-center gap-x-6 gap-y-2.5 text-[14px] max-[560px]:grid-cols-1 max-[560px]:gap-y-1">
              {rows.map(([label, value, copyable]) => (
                <div key={label} className="contents">
                  <dt className="text-spot-body max-[560px]:mt-2">{label}</dt>
                  <dd className="flex min-w-0 items-center gap-2.5 text-spot-ink">
                    <span className="min-w-0 [overflow-wrap:anywhere]">
                      {value}
                    </span>
                    {copyable && (
                      <button
                        type="button"
                        className={rowAction}
                        onClick={copy(label, value)}
                        aria-label={`Copy ${label}`}
                      >
                        <FiCopy aria-hidden="true" /> Copy
                      </button>
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          )}
          {payment.instructions && (
            <p className="mt-4 whitespace-pre-wrap text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]">
              {payment.instructions}
            </p>
          )}
          {payment.payLink && (
            <a
              href={payment.payLink}
              target="_blank"
              rel="noopener noreferrer"
              className={`${primaryButton} mt-5 max-[560px]:w-full`}
            >
              <FiExternalLink aria-hidden="true" /> Pay online
            </a>
          )}
        </div>
        {payment.upi && <UpiCode upi={payment.upi} />}
      </div>
    </section>
  );
};

export default PaymentDetails;
