import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import currencies from "../../../world_currencies.json";
import {
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  errorMessage,
} from "../../../components/workspace/Workspace";
import { primaryButton } from "../../../components/workspace/pastel";
import {
  useGetSettingsQuery,
  useUpdateSettingsMutation,
} from "../settingsApiSlice";
import { numberPreview } from "../numbering";

const browserZone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
};
const timeZones = (current) => {
  let zones = [];
  try {
    zones = Intl.supportedValuesOf("timeZone");
  } catch {
    zones = [];
  }
  return [...new Set(["UTC", current, browserZone(), ...zones])].sort();
};

const NUMBERING = [
  { type: "Invoices", prefix: "invoicePrefix", next: "nextInvoiceNumber" },
  {
    type: "Quotations",
    prefix: "quotationPrefix",
    next: "nextQuotationNumber",
  },
  { type: "Receipts", prefix: "receiptPrefix", next: "nextReceiptNumber" },
];

const SettingsForm = ({ settings }) => {
  const [values, setValues] = useState({
    defaultCurrency: settings.defaultCurrency,
    paymentTermsDays: String(settings.paymentTermsDays),
    defaultTaxRate: String(settings.defaultTaxRate),
    taxLabel: settings.taxLabel,
    defaultNotes: settings.defaultNotes || "",
    defaultTerms: settings.defaultTerms || "",
    emailMessage: settings.emailMessage || "",
    // A user who never saved settings starts with this browser's zone.
    timeZone:
      settings.timeZone === "UTC" && !settings.saved
        ? browserZone()
        : settings.timeZone,
    invoicePrefix: settings.invoicePrefix,
    quotationPrefix: settings.quotationPrefix,
    receiptPrefix: settings.receiptPrefix,
    nextInvoiceNumber: String(settings.nextInvoiceNumber),
    nextQuotationNumber: String(settings.nextQuotationNumber),
    nextReceiptNumber: String(settings.nextReceiptNumber),
  });
  const [save, { isLoading: saving }] = useUpdateSettingsMutation();
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await save({
        ...values,
        paymentTermsDays: Number(values.paymentTermsDays),
        defaultTaxRate: Number(values.defaultTaxRate),
        nextInvoiceNumber: Number(values.nextInvoiceNumber),
        nextQuotationNumber: Number(values.nextQuotationNumber),
        nextReceiptNumber: Number(values.nextReceiptNumber),
      }).unwrap();
      toast.success(result.message || "Your settings were saved.");
    } catch (error) {
      toast.error(errorMessage(error, "Your settings could not be saved."));
    }
  };
  return (
    <form className="flex flex-col gap-6 compact:gap-5" onSubmit={submit}>
      <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,0.8fr)] items-start gap-6 compact:gap-5 max-[1100px]:grid-cols-1">
        <Panel
          title="New documents"
          description="Every new invoice, quotation and receipt starts with these."
        >
          <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
            <Field
              label="Currency"
              as="select"
              value={values.defaultCurrency}
              onChange={change("defaultCurrency")}
              disabled={saving}
            >
              {currencies.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.code} — {item.currency}
                </option>
              ))}
            </Field>
            <Field
              label="Payment terms (days)"
              type="number"
              min="0"
              max="365"
              step="1"
              required
              value={values.paymentTermsDays}
              onChange={change("paymentTermsDays")}
              disabled={saving}
              hint="The due date is this many days after the issue date."
            />
            <Field
              label="Tax name"
              required
              maxLength={20}
              value={values.taxLabel}
              onChange={change("taxLabel")}
              disabled={saving}
              hint="Shown on documents and PDFs, such as VAT or GST."
            />
            <Field
              label="Tax rate (%)"
              type="number"
              min="0"
              max="100"
              step="0.0001"
              required
              value={values.defaultTaxRate}
              onChange={change("defaultTaxRate")}
              disabled={saving}
            />
            <Field
              label="Notes"
              as="textarea"
              maxLength={5000}
              value={values.defaultNotes}
              onChange={change("defaultNotes")}
              disabled={saving}
              className="col-span-full"
            />
            <Field
              label="Terms & conditions"
              as="textarea"
              maxLength={5000}
              value={values.defaultTerms}
              onChange={change("defaultTerms")}
              disabled={saving}
              className="col-span-full"
            />
          </div>
        </Panel>
        <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
          <Panel
            title="Emails"
            description="The message when you email a document."
          >
            <Field
              label="Message"
              as="textarea"
              maxLength={5000}
              value={values.emailMessage}
              onChange={change("emailMessage")}
              disabled={saving}
              hint="{client}, {document} and {business} are filled in for each email. Leave it empty for the standard message."
            />
          </Panel>
          <Panel
            title="Numbering"
            description="New documents count up from these. Numbers already used are skipped."
          >
            <div className="flex flex-col gap-5">
              {NUMBERING.map(({ type, prefix, next }) => (
                <fieldset
                  key={type}
                  className="m-0 grid min-w-0 grid-cols-2 gap-4 border-0 p-0 max-[700px]:grid-cols-1"
                >
                  <legend className="mb-2 text-[13px] text-spot-muted">
                    {type} · next:{" "}
                    <span className="font-medium text-spot-ink">
                      {numberPreview(values[prefix], values[next])}
                    </span>
                  </legend>
                  <Field
                    label={`${type} prefix`}
                    maxLength={20}
                    value={values[prefix]}
                    onChange={change(prefix)}
                    disabled={saving}
                  />
                  <Field
                    label={`Next ${type.toLowerCase().replace(/s$/, "")} number`}
                    type="number"
                    min="1"
                    max="999999999"
                    step="1"
                    required
                    value={values[next]}
                    onChange={change(next)}
                    disabled={saving}
                  />
                </fieldset>
              ))}
            </div>
          </Panel>
          <Panel title="Region" description="Dates on your PDFs follow it.">
            <Field
              label="Time zone"
              as="select"
              value={values.timeZone}
              onChange={change("timeZone")}
              disabled={saving}
            >
              {timeZones(values.timeZone).map((zone) => (
                <option key={zone} value={zone}>
                  {zone.replace(/_/g, " ")}
                </option>
              ))}
            </Field>
          </Panel>
        </div>
      </div>
      <div className="flex justify-end max-[700px]:[&>*]:flex-1">
        <button type="submit" className={primaryButton} disabled={saving}>
          <FiCheck aria-hidden="true" />
          {saving ? "Saving…" : "Save settings"}
        </button>
      </div>
    </form>
  );
};

const SettingsPage = () => {
  useTitle("Settings — Invoice Spot");
  const query = useGetSettingsQuery();
  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="Defaults for your documents and the emails you send."
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <SettingsForm settings={query.data.settings} />
      )}
    </WorkspacePage>
  );
};

export default SettingsPage;
