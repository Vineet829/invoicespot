import { useState } from "react";
import { FiCheck, FiCreditCard } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  primaryButton,
  sectionTitle,
} from "../../../components/workspace/pastel";
import {
  useGetPaymentDetailsQuery,
  useUpdatePaymentDetailsMutation,
} from "../settingsApiSlice";
import SettingsTabs from "../components/SettingsTabs";
import { useBusiness } from "../../business/permissions";

const KEYS = [
  "accountHolder",
  "bankName",
  "accountNumber",
  "bankCodeType",
  "bankCode",
  "bankCodeLabel",
  "upiId",
  "payLink",
  "instructions",
];

// The kinds of bank code the server knows, by the names clients know them.
const CODE_TYPES = [
  ["IFSC", "IFSC"],
  ["SWIFT", "SWIFT / BIC"],
  ["IBAN", "IBAN"],
  ["SORT_CODE", "Sort code"],
  ["ROUTING", "Routing number"],
  ["BSB", "BSB"],
  ["OTHER", "Another code"],
];

const formValues = (details) =>
  Object.fromEntries(KEYS.map((key) => [key, details[key] || ""]));

const PaymentFields = ({ details }) => {
  const [values, setValues] = useState(() => formValues(details));
  const [save, { isLoading: saving }] = useUpdatePaymentDetailsMutation();
  const refusal = useBusiness().reason("MANAGE_BUSINESS");
  const busy = saving || Boolean(refusal);
  const field = (key) => ({
    name: key,
    value: values[key],
    onChange: (event) =>
      setValues((current) => ({ ...current, [key]: event.target.value })),
    disabled: busy,
  });
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await save(values).unwrap();
      // The server writes codes and IDs one standard way, such as an IFSC
      // in capitals; show them as saved.
      setValues(formValues(result.paymentDetails));
      toast.success(result.message || "Your payment details were saved.");
    } catch (error) {
      toast.error(
        errorMessage(error, "Your payment details could not be saved."),
      );
    }
  };
  return (
    <form onSubmit={submit}>
      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
        <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
          {refusal && <p className={panelNote}>{refusal}.</p>}
          <Panel
            title="Bank transfer"
            description="The account clients can pay into."
          >
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="Account holder"
                maxLength={120}
                hint="The name on the account."
                {...field("accountHolder")}
              />
              <Field label="Bank" maxLength={120} {...field("bankName")} />
              <Field
                label="Account number"
                maxLength={34}
                className="col-span-full"
                {...field("accountNumber")}
              />
              <Field label="Bank code" as="select" {...field("bankCodeType")}>
                <option value="">Choose a kind of code</option>
                {CODE_TYPES.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Field>
              <Field
                label="Code"
                maxLength={40}
                hint="Such as an IFSC like HDFC0001234."
                {...field("bankCode")}
              />
              {values.bankCodeType === "OTHER" && (
                <Field
                  label="Name of the code"
                  maxLength={30}
                  hint="As clients should see it, such as Branch code."
                  {...field("bankCodeLabel")}
                />
              )}
            </div>
          </Panel>
          <Panel title="UPI" description="Shown on invoices in Indian rupees.">
            <Field
              label="UPI ID"
              maxLength={100}
              hint="Such as yourname@okhdfcbank."
              {...field("upiId")}
            />
          </Panel>
          <Panel
            title="Online payment"
            description="A page where clients can pay you online."
          >
            <div className="flex flex-col gap-5">
              <Field
                label="Payment link"
                type="url"
                maxLength={300}
                hint="It must start with https://."
                {...field("payLink")}
              />
              <Field
                label="Instructions"
                as="textarea"
                rows={3}
                maxLength={1000}
                hint="Anything else clients should know, such as what to give as the payment reference."
                {...field("instructions")}
              />
            </div>
          </Panel>
          <div className="flex justify-end max-[700px]:[&>*]:flex-1">
            <button
              type="submit"
              className={primaryButton}
              disabled={busy}
              title={refusal}
            >
              <FiCheck aria-hidden="true" />
              {saving ? "Saving…" : "Save changes"}
            </button>
          </div>
        </div>
        <aside className="min-w-0 max-[1359px]:hidden min-[1360px]:sticky min-[1360px]:top-[30px]">
          <div className="rounded-[30px] border border-white/85 bg-white/50 p-6 shadow-panel-soft backdrop-blur-[16px] compact:p-[18px] [&>p]:mt-3 [&>p]:text-[13.5px] [&>p]:leading-[1.7] [&>p]:text-spot-body [&>svg]:mb-4 [&>svg]:size-7 [&>svg]:stroke-[1.4] [&>svg]:text-spot-violet">
            <FiCreditCard aria-hidden="true" />
            <h2 className={sectionTitle}>How clients pay you.</h2>
            <p>
              Unpaid invoices show these details in their PDF and on their share
              link. Leave a field empty to leave it off.
            </p>
          </div>
        </aside>
      </div>
    </form>
  );
};

const PaymentDetailsPage = () => {
  useTitle("Getting paid — Invoice Spot");
  const query = useGetPaymentDetailsQuery();
  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="How clients can pay you, as unpaid invoices show it."
        actions={<SettingsTabs />}
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        query.data?.paymentDetails && (
          <PaymentFields details={query.data.paymentDetails} />
        )
      )}
    </WorkspacePage>
  );
};

export default PaymentDetailsPage;
