import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import {
  useCreatePaymentMutation,
  useUpdateDocMutation,
} from "../documentsApiSlice";
import {
  Field,
  dateInput,
  dateInstant,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { primaryButton } from "../../../components/workspace/pastel";

const PaymentForm = ({ document }) => {
  const [datePaid, setDatePaid] = useState(dateInput());
  const [amountPaid, setAmountPaid] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [additionalInfo, setAdditionalInfo] = useState("");
  const [create, { isLoading: creating }] = useCreatePaymentMutation();
  const [update, { isLoading: updating }] = useUpdateDocMutation();
  const busy = creating || updating;
  const submit = async (event) => {
    event.preventDefault();
    const totalReceived =
      (document.paymentRecords || []).reduce(
        (sum, payment) => sum + Number(payment.amountPaid || 0),
        0,
      ) + Number(amountPaid);
    let recorded = false;
    try {
      await create({
        id: document._id,
        paidBy: document.customer?.name,
        datePaid: dateInstant(datePaid),
        amountPaid: Number(amountPaid),
        paymentMethod,
        additionalInfo,
      }).unwrap();
      recorded = true;
      await update({
        id: document._id,
        totalAmountReceived: totalReceived,
        status: totalReceived >= document.total ? "Paid" : "Not Fully Paid",
      }).unwrap();
      toast.success("Payment has been recorded successfully.");
    } catch (error) {
      toast.error(
        recorded
          ? "Payment was recorded, but the document status could not be updated. Refresh before recording another payment."
          : errorMessage(error, "The payment could not be recorded."),
      );
    } finally {
      if (recorded) {
        setAmountPaid("");
        setPaymentMethod("");
        setAdditionalInfo("");
      }
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
          step="any"
          required
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
          {[
            "Mobile Money",
            "Cash",
            "Bank Transfer",
            "PayPal",
            "Credit Card",
            "Others",
          ].map((method) => (
            <option key={method}>{method}</option>
          ))}
        </Field>
        <Field
          label="Additional info"
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
