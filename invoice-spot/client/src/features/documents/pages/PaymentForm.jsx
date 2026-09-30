import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { useCreatePaymentMutation } from "../documentsApiSlice";
import { PAYMENT_METHODS } from "../paymentMethods";
import {
  Field,
  dateInput,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import { primaryButton } from "../../../components/workspace/pastel";

const PaymentForm = ({ document }) => {
  const balance = Math.max(
    0,
    Number(document.total || 0) - Number(document.totalAmountReceived || 0),
  );
  const [datePaid, setDatePaid] = useState(dateInput());
  const [amountPaid, setAmountPaid] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [additionalInfo, setAdditionalInfo] = useState("");
  const [create, { isLoading: busy }] = useCreatePaymentMutation();
  const submit = async (event) => {
    event.preventDefault();
    try {
      await create({
        id: document._id,
        datePaid,
        amountPaid: Number(amountPaid),
        paymentMethod,
        additionalInfo,
      }).unwrap();
      toast.success("Payment has been recorded successfully.");
      setAmountPaid("");
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
          value={datePaid}
          onChange={(event) => setDatePaid(event.target.value)}
          disabled={busy}
        />
        <Field
          label="Amount paid"
          type="number"
          min="0.01"
          max={balance}
          step="0.01"
          required
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
      </div>
      <div className="mt-5 flex justify-end max-[700px]:[&>*]:flex-1">
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || !amountPaid || !paymentMethod}
        >
          <FiCheck aria-hidden="true" />
          {busy ? "Recording…" : "Record payment"}
        </button>
      </div>
    </form>
  );
};
export default PaymentForm;
