import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { useCreatePaymentMutation } from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";
import { TAX_WITHHELD_HINT, TAX_WITHHELD_LABEL, cents } from "../taxWithheld";
import {
  Field,
  dateInput,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import { primaryButton, textLink } from "../../../components/workspace/pastel";

const PaymentForm = ({ document }) => {
  const balance = Number(document.ledger?.balanceDue || 0);
  const [datePaid, setDatePaid] = useState(dateInput());
  const [amountPaid, setAmountPaid] = useState("");
  // The tax the client withheld, or null while its field is hidden.
  const [tdsAmount, setTdsAmount] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState("");
  const [additionalInfo, setAdditionalInfo] = useState("");
  const [create, { isLoading: busy }] = useCreatePaymentMutation();
  const withholding = tdsAmount !== null;
  const settles = cents(amountPaid) + cents(tdsAmount);
  const submit = async (event) => {
    event.preventDefault();
    try {
      await create({
        id: document._id,
        datePaid,
        amountPaid: Number(amountPaid),
        tdsAmount: Number(tdsAmount),
        paymentMethod,
        additionalInfo,
      }).unwrap();
      toast.success("Payment has been recorded successfully.");
      setAmountPaid("");
      setTdsAmount(null);
      setPaymentMethod("");
      setAdditionalInfo("");
    } catch (error) {
      toast.error(errorMessage(error, "The payment could not be recorded."));
    }
  };
  return (
    <form onSubmit={submit}>
      <div className="grid grid-cols-4 gap-5 max-[1359px]:grid-cols-2 max-[700px]:grid-cols-1">
        <Field
          label="Date paid"
          type="date"
          required
          max={dateInput()}
          value={datePaid}
          onChange={(event) => setDatePaid(event.target.value)}
          disabled={busy}
        />
        <Field
          label="Amount paid"
          type="number"
          min={withholding ? "0" : "0.01"}
          max={cents(balance - cents(tdsAmount))}
          step="0.01"
          required={!withholding}
          hint={`Up to ${money(balance, document.currency)} is still owed.`}
          value={amountPaid}
          onChange={(event) => setAmountPaid(event.target.value)}
          disabled={busy}
        />
        <Field
          label="Payment method"
          as="select"
          required
          value={paymentMethod}
          onChange={(event) => setPaymentMethod(event.target.value)}
          disabled={busy}
        >
          <option value="">Choose a method</option>
          {PAYMENT_METHODS.map((method) => (
            <option key={method}>{method}</option>
          ))}
        </Field>
        <Field
          label="Additional info"
          maxLength={255}
          value={additionalInfo}
          onChange={(event) => setAdditionalInfo(event.target.value)}
          disabled={busy}
        />
        {withholding && (
          <Field
            id="payment-tds"
            label={TAX_WITHHELD_LABEL}
            type="number"
            min="0"
            max={cents(balance - cents(amountPaid))}
            step="0.01"
            hint={TAX_WITHHELD_HINT}
            value={tdsAmount}
            onChange={(event) => setTdsAmount(event.target.value)}
            disabled={busy}
          />
        )}
      </div>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-4 max-[700px]:flex-col max-[700px]:items-stretch">
        <button
          type="button"
          className={`${textLink} self-start bg-transparent p-0 text-[13px] coarse:min-h-11`}
          aria-expanded={withholding}
          aria-controls={withholding ? "payment-tds" : undefined}
          onClick={() => setTdsAmount(withholding ? null : "")}
          disabled={busy}
        >
          {withholding
            ? "Remove the tax withheld"
            : "Add tax withheld by the client (TDS)"}
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || settles <= 0 || !paymentMethod}
        >
          <FiCheck aria-hidden="true" />
          {busy ? "Recording…" : "Record payment"}
        </button>
      </div>
    </form>
  );
};
export default PaymentForm;
