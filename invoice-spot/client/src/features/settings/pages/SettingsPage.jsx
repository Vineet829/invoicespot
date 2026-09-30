import { useState } from "react";
import {
  FiArchive,
  FiArrowDownLeft,
  FiCheck,
  FiDownload,
  FiFileText,
  FiUsers,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useFileDownload from "../../../hooks/useFileDownload";
import currencies from "../../../world_currencies.json";
import {
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  dateInput,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useGetDocumentsCsvMutation,
  useGetPaymentsCsvMutation,
} from "../../documents/documentsApiSlice";
import { useGetCustomersCsvMutation } from "../../customers/customersApiSlice";
import {
  useGetDataExportMutation,
  useGetSettingsQuery,
  useUpdateSettingsMutation,
} from "../settingsApiSlice";
import { numberPreview } from "../numbering";
import { reminderSummary } from "../reminders";

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
    remindersEnabled: settings.remindersEnabled,
    reminderDaysBefore: String(settings.reminderDaysBefore),
    reminderOnDue: settings.reminderOnDue,
    reminderEveryDays: String(settings.reminderEveryDays),
    reminderMax: String(settings.reminderMax),
  });
  const [save, { isLoading: saving }] = useUpdateSettingsMutation();
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const toggle = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.checked }));
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
        reminderDaysBefore: Number(values.reminderDaysBefore),
        reminderEveryDays: Number(values.reminderEveryDays),
        reminderMax: Number(values.reminderMax),
      }).unwrap();
      toast.success(result.message || "Your settings were saved.");
    } catch (error) {
      toast.error(errorMessage(error, "Your settings could not be saved."));
    }
  };
  return (
    <form className="flex flex-col gap-6 compact:gap-5" onSubmit={submit}>
      <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,0.8fr)] items-start gap-6 compact:gap-5 max-[1100px]:grid-cols-1">
        <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
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
          <Panel
            title="Payment reminders"
            description="Emails to clients about unpaid invoices, with the invoice attached. They are checked once a day in your time zone."
          >
            <label className="flex items-center gap-3 text-[14px] text-spot-ink">
              <input
                type="checkbox"
                className="size-[18px] shrink-0 accent-spot-violet"
                checked={values.remindersEnabled}
                onChange={toggle("remindersEnabled")}
                disabled={saving}
              />
              Send reminders automatically
            </label>
            <fieldset
              className="m-0 mt-5 grid min-w-0 grid-cols-2 gap-5 border-0 p-0 disabled:opacity-60 max-[700px]:grid-cols-1"
              disabled={saving || !values.remindersEnabled}
            >
              <legend className="sr-only">When reminders go out</legend>
              <Field
                label="Days before the due date"
                type="number"
                min="0"
                max="60"
                step="1"
                required
                value={values.reminderDaysBefore}
                onChange={change("reminderDaysBefore")}
                hint="0 sends none before."
              />
              <Field
                label="Then every (days)"
                type="number"
                min="0"
                max="60"
                step="1"
                required
                value={values.reminderEveryDays}
                onChange={change("reminderEveryDays")}
                hint="Counted from the due date. 0 sends none after it."
              />
              <Field
                label="At most, per invoice"
                type="number"
                min="1"
                max="10"
                step="1"
                required
                value={values.reminderMax}
                onChange={change("reminderMax")}
              />
              <label className="flex items-center gap-3 self-center text-[13px] text-spot-body">
                <input
                  type="checkbox"
                  className="size-[18px] shrink-0 accent-spot-violet"
                  checked={values.reminderOnDue}
                  onChange={toggle("reminderOnDue")}
                />
                On the due date too
              </label>
            </fieldset>
            <p
              className="mt-5 text-[13px] leading-[1.6] text-spot-muted"
              role="status"
            >
              {reminderSummary(values)}
            </p>
          </Panel>
        </div>
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

// Everything kept in the app, as CSV files for a spreadsheet or another app.
const YourData = () => {
  const [documents, documentsBusy] = useFileDownload(
    useGetDocumentsCsvMutation,
  );
  const [payments, paymentsBusy] = useFileDownload(useGetPaymentsCsvMutation);
  const [clients, clientsBusy] = useFileDownload(useGetCustomersCsvMutation);
  const [everything, everythingBusy] = useFileDownload(
    useGetDataExportMutation,
  );
  const today = dateInput();
  const files = [
    {
      name: "Documents",
      detail: "Every invoice, quotation and receipt",
      icon: FiFileText,
      busy: documentsBusy,
      download: () => documents({}, `invoice-spot-documents-${today}.csv`),
    },
    {
      name: "Payments",
      detail: "Every payment recorded on them",
      icon: FiArrowDownLeft,
      busy: paymentsBusy,
      download: () => payments(null, `invoice-spot-payments-${today}.csv`),
    },
    {
      name: "Clients",
      detail: "Contact details and what each owes",
      icon: FiUsers,
      busy: clientsBusy,
      download: () => clients({}, `invoice-spot-clients-${today}.csv`),
    },
  ];
  return (
    <Panel
      title="Your data"
      description="Download what you keep in Invoice Spot as CSV files, which open in any spreadsheet, or everything at once as a ZIP file with your items, recurring invoices, settings and profile too."
      aside={
        <button
          type="button"
          className={`${secondaryButton} shrink-0`}
          onClick={() => everything(null, `invoice-spot-export-${today}.zip`)}
          disabled={everythingBusy}
        >
          <FiArchive aria-hidden="true" /> Download everything
        </button>
      }
    >
      <ul className="m-0 grid list-none grid-cols-3 gap-4 p-0 max-[1100px]:grid-cols-1">
        {files.map(({ name, detail, icon: Icon, busy, download }) => (
          <li
            key={name}
            className="flex min-w-0 items-center justify-between gap-4 rounded-[18px] border border-white/90 bg-white/60 px-4 py-3.5"
          >
            <span className="flex min-w-0 items-center gap-3">
              <span
                className="grid size-10 shrink-0 place-items-center rounded-xl bg-spot-violet/[.08] text-spot-violet [&>svg]:size-[18px] [&>svg]:stroke-[1.7]"
                aria-hidden="true"
              >
                <Icon />
              </span>
              <span className="min-w-0">
                <span className="block text-[14.5px] font-medium text-spot-ink">
                  {name}
                </span>
                <span className={`block ${panelNote}`}>{detail}</span>
              </span>
            </span>
            <button
              type="button"
              className={secondaryButton}
              onClick={download}
              disabled={busy}
              aria-label={`Download ${name.toLowerCase()} as CSV`}
            >
              <FiDownload aria-hidden="true" /> CSV
            </button>
          </li>
        ))}
      </ul>
    </Panel>
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
      <YourData />
    </WorkspacePage>
  );
};

export default SettingsPage;
