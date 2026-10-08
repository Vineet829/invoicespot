import { useEffect, useId, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  FiBookmark,
  FiCheck,
  FiFileMinus,
  FiFileText,
  FiLock,
  FiPlus,
  FiRepeat,
  FiSlash,
  FiTrash2,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import currencies from "../../../world_currencies.json";
import {
  useCreateDocMutation,
  useGetSingleDocQuery,
  useGetTaxRatesQuery,
  useIssueDocMutation,
  useUpdateDocMutation,
} from "../documentsApiSlice";
import { isDraft, isVoid } from "../documentState";
import { useGetWorkspaceCustomersQuery } from "../../workspace/workspaceApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import { useGetBusinessQuery } from "../../business/businessApiSlice";
import { useGetGstStatesQuery } from "../../gst/gstApiSlice";
import { isValidGstin, normalizeGstin } from "../../gst/gstin";
import {
  HSN_SAC_PATTERN,
  isValidHsnSac,
  normalizeHsnSac,
} from "../../gst/hsnSac";
import { taxRows } from "../gstRows";
import NewClientDialog from "../../customers/components/NewClientDialog";
import { matchClients } from "./documentEditorModel";
import { useBusiness } from "../../business/permissions";
import useBusinessToday from "../../business/useBusinessToday";
import {
  useCreateItemMutation,
  useGetItemsQuery,
} from "../../items/itemsApiSlice";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  Field,
  LoadingState,
  ErrorState,
  EmptyState,
  StatusBadge,
  Amount,
  dateInput,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  panelNote,
  primaryButton,
  rowAction,
  secondaryButton,
  textLink,
} from "../../../components/workspace/pastel";
import {
  creditNotePayload,
  documentPayload,
  documentTotals,
  gstPlace,
  gstSupply,
  gstTotals,
  lineAmount,
} from "./documentEditorModel";

const detailLine =
  "text-[13.5px] leading-[1.7] text-spot-body [overflow-wrap:anywhere]";
const removeItemButton =
  "inline-flex size-11 shrink-0 items-center justify-center rounded-xl bg-overdue/70 text-overdue-strong transition-colors hover:enabled:bg-overdue disabled:cursor-default disabled:text-overdue-muted disabled:opacity-100 [&>svg]:size-[17px] [&>svg]:stroke-[1.7]";

const clientOption =
  "flex w-full flex-col items-start gap-0.5 rounded-xl border border-white/90 bg-white/60 px-3.5 py-2.5 text-left transition-colors hover:enabled:bg-white disabled:cursor-default";

const saveItemButton =
  "inline-flex size-11 shrink-0 items-center justify-center rounded-xl bg-spot-violet/[.07] text-spot-violet transition-colors hover:enabled:bg-spot-violet/[.13] disabled:cursor-default disabled:text-spot-disabled disabled:opacity-100 [&>svg]:size-[17px] [&>svg]:stroke-[1.7]";

const blankItem = (taxRate) => ({
  itemName: "",
  unitPrice: "",
  quantity: "",
  unit: "",
  discount: "",
  taxRate,
});
// A line field's label. The lines sit one under another, so the line's
// number is only read out, to tell same-named fields apart.
const lineLabel = (text, index) => (
  <>
    {text}
    <span className="sr-only"> {index + 1}</span>
  </>
);
// A new document starts from the business settings, and from a client when
// it was started from their page; a draft keeps its own values. Either is
// saved as a draft, or saved and issued.
const DocumentFields = ({
  doc,
  settings,
  customers,
  initialCustomer,
  onSubmit,
  busy,
}) => {
  const [customer, setCustomer] = useState(
    doc ? { ...doc.customer, _id: doc.customerId } : initialCustomer || null,
  );
  const [documentType, setDocumentType] = useState(
    doc?.documentType || "Invoice",
  );
  const [currency, setCurrency] = useState(
    doc?.currency || settings?.defaultCurrency || currencies[0].code,
  );
  // The rate to the reporting currency, kept only while it was typed
  // against the one the business reports in now.
  const base = settings?.baseCurrency || settings?.defaultCurrency;
  const [exchangeRate, setExchangeRate] = useState(
    doc?.exchangeRate && doc.exchangeBase === base ? doc.exchangeRate : "",
  );
  const foreign = currency !== base;
  const [issueDate, setIssueDate] = useState(doc?.issueDate || "");
  // Today by the business's calendar: the server dates a document left
  // undated on it, and refuses one dated after it.
  const businessToday = useBusinessToday();
  const today = dateInput(businessToday);
  // A credit note keeps its invoice's client and currency; its lines, date,
  // notes and reason change, up to what is left to credit.
  const creditNote = doc?.documentType === "Credit Note";
  const credited = doc?.creditedDocument;
  const [creditReason, setCreditReason] = useState(doc?.creditReason || "");
  // A new document is due the payment terms after today.
  const [dueDate, setDueDate] = useState(
    dateInput(
      doc?.dueDate ||
        new Date(
          businessToday.getFullYear(),
          businessToday.getMonth(),
          businessToday.getDate() + (settings?.paymentTermsDays ?? 7),
        ),
    ),
  );
  // A due date comes no earlier than the issue date, today until one is
  // chosen. A draft saved due earlier keeps it while neither date changes,
  // as the server allows.
  const earliestDue =
    doc?.dueDate === dueDate && (doc?.issueDate || "") === issueDate
      ? undefined
      : issueDate || today;
  // The rate new lines start at, which every line can be set to at once.
  const [everyRate, setEveryRate] = useState(
    doc?.rates ?? String(settings?.defaultTaxRate ?? "0"),
  );
  const [items, setItems] = useState(
    doc?.billingItems?.map((item) => ({ ...item })) || [blankItem(everyRate)],
  );
  const [additionalInfo, setAdditionalInfo] = useState(
    (doc ? doc.additionalInfo : settings?.defaultNotes) || "",
  );
  const [termsConditions, setTermsConditions] = useState(
    (doc ? doc.termsConditions : settings?.defaultTerms) || "",
  );
  const [showPaymentDetails, setShowPaymentDetails] = useState(
    doc?.showPaymentDetails ?? true,
  );
  const taxLabel = settings?.taxLabel || "Tax";
  // India GST mode: where the supply goes, as the server works it out, and
  // how it is taxed. A credit note keeps its invoice's supply.
  const business = useGetBusinessQuery().data?.business;
  const gstStates =
    useGetGstStatesQuery(undefined, {
      skip: settings?.taxRegime !== "GST_INDIA",
    }).data?.states || [];
  const gstMode = settings?.taxRegime === "GST_INDIA" && !creditNote;
  const sellerState = isValidGstin(business?.taxId)
    ? normalizeGstin(business.taxId).slice(0, 2)
    : "";
  const workedOut = gstPlace({ customer, sellerState });
  const [placeChoice, setPlaceChoice] = useState(() => {
    const saved = doc?.gst?.placeOfSupply;
    const automatic = gstPlace({
      customer: doc?.customer,
      sellerState: doc?.gst?.sellerStateCode,
    });
    return saved && saved !== automatic ? saved : "";
  });
  const [reverseCharge, setReverseCharge] = useState(
    Boolean(doc?.gst?.reverseCharge),
  );
  const [exportType, setExportType] = useState(
    doc?.gst?.supplyType === "EXPORT_IGST" ||
      (doc?.gst?.supplyType !== "EXPORT_LUT" && !settings?.lutReference)
      ? "IGST"
      : "LUT",
  );
  const place = placeChoice || workedOut;
  const supply = gstMode
    ? gstSupply({ place, sellerState, exportType })
    : creditNote
      ? doc?.gst?.supplyType
      : null;
  const recipientPays = creditNote
    ? Boolean(doc?.gst?.reverseCharge)
    : reverseCharge && supply !== "EXPORT_LUT" && supply !== "EXPORT_IGST";
  const taxInfo = supply && {
    supplyType: supply,
    reverseCharge: recipientPays,
    unionTerritory: creditNote
      ? doc?.gst?.unionTerritory
      : supply === "INTRA" &&
        gstStates.find((state) => state.code === place)?.unionTerritory,
  };
  const placeName = (code) =>
    gstStates.find((state) => state.code === code)?.name || code;
  const totals = supply
    ? gstTotals(items, supply, recipientPays)
    : documentTotals(items);
  const overCredit =
    creditNote && Number(totals.total) > Number(credited.creditable);
  const catalogListId = useId();
  const ratesListId = useId();
  const unitsListId = useId();
  const suggestions = useGetTaxRatesQuery().data;
  const lineId = useId();
  // The line to focus after it is added; it goes at the bottom.
  const [focusLine, setFocusLine] = useState(null);
  const [addingClient, setAddingClient] = useState(false);
  const [clientQuery, setClientQuery] = useState("");
  const clientMatches = matchClients(customers, clientQuery);
  useEffect(() => {
    if (focusLine === null) return;
    document.getElementById(`${lineId}-${focusLine}`)?.focus();
    setFocusLine(null);
  }, [focusLine, lineId]);
  const catalogItems = useGetItemsQuery().data?.items || [];
  const [createItem, { isLoading: savingItem }] = useCreateItemMutation();
  const catalogMatch = (name) =>
    catalogItems.find(
      (entry) => entry.name.toLowerCase() === name.trim().toLowerCase(),
    );
  // A GST document's lines each carry the HSN or SAC code its returns are
  // filed by, suggested from the codes the business's items use; a credit
  // note keeps its invoice's.
  const hsnLines = Boolean(supply);
  const hsnListId = useId();
  const hsnCodes = Object.entries(
    catalogItems.reduce((codes, entry) => {
      if (entry.hsnSac) (codes[entry.hsnSac] ||= []).push(entry.name);
      return codes;
    }, {}),
  );
  // Picking a saved item fills in its price, a quantity of one, its unit
  // and HSN/SAC code where the line has none yet, and its tax rate unless
  // the line's rate was changed from the one new lines start at.
  const chooseName = (index, name) =>
    setItems((current) =>
      current.map((item, i) => {
        if (i !== index) return item;
        const match = catalogMatch(name);
        return {
          ...item,
          itemName: name,
          ...(match && (item.unitPrice ?? "") === ""
            ? { unitPrice: String(match.unitPrice) }
            : {}),
          ...(match && (item.quantity ?? "") === "" ? { quantity: "1" } : {}),
          ...(match?.unit && !item.unit ? { unit: match.unit } : {}),
          ...(match?.hsnSac && !item.hsnSac?.trim()
            ? { hsnSac: match.hsnSac }
            : {}),
          ...(match?.taxRate != null &&
          String(item.taxRate ?? "") === String(everyRate)
            ? { taxRate: String(match.taxRate) }
            : {}),
        };
      }),
    );
  const saveToCatalog = async (item) => {
    try {
      await createItem({
        name: item.itemName.trim(),
        unitPrice: Number(item.unitPrice) || 0,
        ...(isValidHsnSac(item.hsnSac)
          ? { hsnSac: normalizeHsnSac(item.hsnSac) }
          : {}),
      }).unwrap();
      toast.success(`${item.itemName.trim()} was saved to your items.`);
    } catch (error) {
      toast.error(errorMessage(error, "The item could not be saved."));
    }
  };
  const status =
    documentType === "Receipt" ? "Paid" : doc?.status || "Not Paid";
  const changeItem = (index, key, value) =>
    setItems((current) =>
      current.map((item, i) =>
        i === index ? { ...item, [key]: value } : item,
      ),
    );
  return (
    <>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!customer) {
            toast.error("Select a customer before saving the document.");
            return;
          }
          const payload = documentPayload({
            customer,
            documentType,
            issueDate,
            dueDate: dueDate || null,
            currency,
            exchangeRate: foreign ? exchangeRate : "",
            items,
            additionalInfo,
            termsConditions,
            showPaymentDetails,
          });
          onSubmit(
            creditNote
              ? creditNotePayload(payload, creditReason)
              : {
                  ...payload,
                  ...(gstMode && {
                    placeOfSupply: placeChoice,
                    reverseCharge: recipientPays,
                    ...(place === "96" && { exportType }),
                  }),
                },
            { issue: event.nativeEvent.submitter?.value !== "draft" },
          );
        }}
      >
        <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
          <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
            {creditNote ? (
              <Panel
                title="Credit note details"
                description="It keeps the client and currency of what it credits."
              >
                <div className="grid grid-cols-2 gap-8 max-[700px]:grid-cols-1 max-[700px]:gap-6">
                  <div className="flex min-w-0 flex-col gap-5">
                    <p className="m-0 rounded-2xl border border-white/90 bg-white/60 p-4 text-[14px] leading-[1.6] text-spot-body [overflow-wrap:anywhere]">
                      Credit note for{" "}
                      <Link
                        className={textLink}
                        to={`/document/${credited._id}`}
                      >
                        {credited.documentNumber}
                      </Link>{" "}
                      · {customer?.name} · {currency}
                    </p>
                    <Field
                      label="Reason"
                      as="textarea"
                      rows="4"
                      maxLength={500}
                      value={creditReason}
                      onChange={(event) => setCreditReason(event.target.value)}
                      hint="Printed on the credit note, such as returned goods or a price corrected."
                      disabled={busy}
                    />
                  </div>
                  <div className="flex min-w-0 flex-col gap-5">
                    <Field
                      label="Issue date"
                      type="date"
                      value={issueDate}
                      min={credited.issueDate || undefined}
                      max={today}
                      onChange={(event) => setIssueDate(event.target.value)}
                      hint={`Leave it empty to date it the day it’s issued. It can’t be before ${credited.documentNumber} or later than today.`}
                      disabled={busy}
                    />
                  </div>
                </div>
              </Panel>
            ) : (
              <Panel
                title="Document details"
                description="Choose the document and who it’s for."
              >
                <div className="grid grid-cols-2 gap-8 max-[700px]:grid-cols-1 max-[700px]:gap-6">
                  <div className="flex min-w-0 flex-col gap-5">
                    <Field
                      label="Document type"
                      as="select"
                      value={documentType}
                      onChange={(event) => setDocumentType(event.target.value)}
                      disabled={busy}
                    >
                      {["Invoice", "Receipt", "Quotation"].map((type) => (
                        <option key={type}>{type}</option>
                      ))}
                    </Field>
                    <div className="min-w-0">
                      {customer ? (
                        <div className="rounded-2xl border border-white/90 bg-white/60 p-4">
                          <strong className="mb-1.5 block text-[15px] font-medium text-spot-ink [overflow-wrap:anywhere]">
                            {customer.name}
                          </strong>
                          <p className={detailLine}>{customer.email}</p>
                          <p className={detailLine}>
                            Account no. {customer.accountNo || "—"}
                          </p>
                          <p className={detailLine}>
                            VAT / TIN: {customer.vatTinNo || "—"}
                          </p>
                          {customer.gstin && (
                            <p className={detailLine}>
                              GSTIN: {customer.gstin}
                            </p>
                          )}
                          <p className={detailLine}>{customer.phoneNumber}</p>
                          <p className={detailLine}>{customer.address}</p>
                          <button
                            type="button"
                            className={`${rowAction} mt-3`}
                            onClick={() => setCustomer(null)}
                            disabled={busy}
                          >
                            <FiRepeat aria-hidden="true" /> Choose another
                            customer
                          </button>
                        </div>
                      ) : (
                        <div className="flex flex-col items-start gap-3">
                          <Field
                            label="Find a client"
                            type="search"
                            autoComplete="off"
                            placeholder="Name, email or account number"
                            value={clientQuery}
                            onChange={(event) =>
                              setClientQuery(event.target.value)
                            }
                            disabled={busy}
                            className="w-full"
                          />
                          {clientMatches.length > 0 && (
                            <ul
                              className="m-0 flex w-full list-none flex-col gap-1.5 p-0"
                              aria-label="Matching clients"
                            >
                              {clientMatches.slice(0, 6).map((client) => (
                                <li key={client._id}>
                                  <button
                                    type="button"
                                    className={clientOption}
                                    onClick={() => setCustomer(client)}
                                    disabled={busy}
                                  >
                                    <span className="text-[14px] font-medium text-spot-ink [overflow-wrap:anywhere]">
                                      {client.name}
                                    </span>
                                    <span className="text-[13px] text-spot-muted [overflow-wrap:anywhere]">
                                      {client.email}
                                    </span>
                                  </button>
                                </li>
                              ))}
                            </ul>
                          )}
                          <p className={panelNote} role="status">
                            {clientMatches.length === 0
                              ? customers.length
                                ? `No client matches “${clientQuery.trim()}”.`
                                : "You have no clients yet."
                              : clientMatches.length > 6
                                ? `${clientMatches.length - 6} more — keep typing to narrow them down.`
                                : ""}
                          </p>
                          <button
                            type="button"
                            className={rowAction}
                            onClick={() => setAddingClient(true)}
                          >
                            <FiPlus aria-hidden="true" /> Add new customer
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex min-w-0 flex-col gap-5">
                    <Field
                      label="Issue date"
                      type="date"
                      required={Boolean(doc?.issueDate)}
                      value={issueDate}
                      max={today}
                      onChange={(event) => setIssueDate(event.target.value)}
                      hint={
                        doc?.issueDate
                          ? "The day the document is dated. It can’t be later than today."
                          : "Leave it empty to date it the day it’s issued. It can’t be later than today."
                      }
                      disabled={busy}
                    />
                    {/* A receipt is paid as it is made, so it has no due
                        date; a quotation is valid until a day instead. */}
                    {documentType !== "Receipt" && (
                      <Field
                        label={
                          documentType === "Quotation"
                            ? "Valid until"
                            : "Due date"
                        }
                        type="date"
                        required={documentType === "Invoice"}
                        min={earliestDue}
                        value={dueDate}
                        onChange={(event) => setDueDate(event.target.value)}
                        disabled={busy}
                      />
                    )}
                    <dl className="grid gap-4">
                      <div>
                        <dt className={panelNote}>Payment status</dt>
                        <dd className="mt-1.5">
                          <StatusBadge status={status} />
                        </dd>
                      </div>
                    </dl>
                  </div>
                </div>
              </Panel>
            )}
            <Panel
              title="Products & services"
              description="The details behind the total."
            >
              <datalist id={catalogListId}>
                {catalogItems.map((entry) => (
                  <option key={entry._id} value={entry.name}>
                    {money(entry.unitPrice)}
                  </option>
                ))}
              </datalist>
              <datalist id={ratesListId}>
                {(suggestions?.rates || []).map((rate) => (
                  <option key={rate.rate} value={rate.rate} />
                ))}
              </datalist>
              <datalist id={unitsListId}>
                {(suggestions?.units || []).map((unit) => (
                  <option key={unit} value={unit} />
                ))}
              </datalist>
              <datalist id={hsnListId}>
                {hsnCodes.map(([code, names]) => (
                  <option key={code} value={code}>
                    {names.join(", ")}
                  </option>
                ))}
              </datalist>
              <div className="[container-name:items] [container-type:inline-size]">
                {items.map((item, index) => (
                  <div
                    className="grid grid-cols-[minmax(160px,2fr)_minmax(72px,1fr)_minmax(64px,0.8fr)_repeat(2,minmax(72px,1fr))_minmax(64px,0.8fr)_minmax(110px,1.1fr)_96px] items-end gap-3 border-t border-[#23273512] py-4 first:border-t-0 first:pt-0 items-md:grid-cols-4 items-sm:grid-cols-2"
                    key={index}
                  >
                    <Field
                      label={lineLabel("Product / service", index)}
                      id={`${lineId}-${index}`}
                      required
                      maxLength={255}
                      list={catalogListId}
                      autoComplete="off"
                      value={item.itemName || ""}
                      onChange={(event) =>
                        chooseName(index, event.target.value)
                      }
                      disabled={busy}
                      className="items-md:col-span-full"
                    />
                    {hsnLines && (
                      <Field
                        label={lineLabel("HSN/SAC", index)}
                        inputMode="numeric"
                        maxLength={12}
                        pattern={HSN_SAC_PATTERN}
                        title="4 to 8 digits"
                        list={hsnListId}
                        autoComplete="off"
                        value={item.hsnSac || ""}
                        onChange={(event) =>
                          changeItem(index, "hsnSac", event.target.value)
                        }
                        disabled={busy}
                        error={
                          item.hsnSac?.trim() && !isValidHsnSac(item.hsnSac)
                            ? "Enter 4 to 8 digits."
                            : null
                        }
                        placeholder="Needed for GST returns"
                        className="col-start-1 row-start-2 items-md:col-span-2 items-md:col-start-auto items-md:row-start-auto items-sm:col-span-full"
                      />
                    )}
                    <Field
                      label={lineLabel("Quantity", index)}
                      max="1000000"
                      type="number"
                      min="0"
                      step="0.0001"
                      value={item.quantity ?? ""}
                      onChange={(event) =>
                        changeItem(index, "quantity", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={lineLabel("Unit", index)}
                      maxLength={20}
                      list={unitsListId}
                      autoComplete="off"
                      value={item.unit || ""}
                      onChange={(event) =>
                        changeItem(index, "unit", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={lineLabel("Unit price", index)}
                      type="number"
                      min="0"
                      step="0.0001"
                      value={item.unitPrice ?? ""}
                      onChange={(event) =>
                        changeItem(index, "unitPrice", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={lineLabel("Discount (%)", index)}
                      type="number"
                      min="0"
                      max="100"
                      step="0.0001"
                      value={item.discount ?? ""}
                      onChange={(event) =>
                        changeItem(index, "discount", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={lineLabel(`${taxLabel} %`, index)}
                      type="number"
                      min="0"
                      max="100"
                      step="0.0001"
                      list={ratesListId}
                      value={item.taxRate ?? ""}
                      onChange={(event) =>
                        changeItem(index, "taxRate", event.target.value)
                      }
                      disabled={busy}
                    />
                    <div className="flex min-w-0 flex-col gap-2">
                      <span className={fieldLabel}>Line total</span>
                      <strong className="flex h-11 items-center justify-end font-normal text-spot-ink items-md:justify-start">
                        <Amount
                          value={lineAmount(item)}
                          currency={currency}
                          hideCode
                          className="text-[17px] font-light tracking-[-0.03em]"
                        />
                      </strong>
                    </div>
                    <div className="flex justify-end gap-2 items-md:col-start-4 items-md:justify-self-end items-sm:col-start-2">
                      {item.itemName?.trim() &&
                        !catalogMatch(item.itemName) && (
                          <button
                            type="button"
                            className={saveItemButton}
                            aria-label={`Save ${item.itemName.trim()} to your items`}
                            title="Save to your items"
                            onClick={() => saveToCatalog(item)}
                            disabled={busy || savingItem}
                          >
                            <FiBookmark aria-hidden="true" />
                          </button>
                        )}
                      <button
                        type="button"
                        className={removeItemButton}
                        aria-label={`Remove item ${index + 1}`}
                        title={
                          items.length === 1
                            ? "A document needs at least one line"
                            : undefined
                        }
                        onClick={() =>
                          setItems((current) =>
                            current.filter((_item, i) => i !== index),
                          )
                        }
                        disabled={busy || items.length === 1}
                      >
                        <FiTrash2 aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-2 border-t border-[#23273512] pt-4">
                <button
                  type="button"
                  className={rowAction}
                  onClick={() => {
                    setItems((current) => [...current, blankItem(everyRate)]);
                    setFocusLine(items.length);
                  }}
                  disabled={busy}
                >
                  <FiPlus aria-hidden="true" /> Add product or service
                </button>
                {creditNote && (
                  <button
                    type="button"
                    className={`${rowAction} ml-2`}
                    onClick={() => {
                      setItems([
                        {
                          ...blankItem(everyRate),
                          itemName: `Credit on ${credited.documentNumber}`,
                          quantity: "1",
                        },
                      ]);
                      setFocusLine(0);
                    }}
                    disabled={busy}
                  >
                    <FiFileMinus aria-hidden="true" /> Credit one amount instead
                  </button>
                )}
              </div>
            </Panel>
            <Panel title="Tax & currency">
              <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
                <div className="flex min-w-0 items-start gap-2.5">
                  <Field
                    label={`${taxLabel} rate for new lines (%)`}
                    max="100"
                    type="number"
                    min="0"
                    step="0.0001"
                    value={everyRate}
                    onChange={(event) => setEveryRate(event.target.value)}
                    hint="Each line keeps its own rate."
                    disabled={busy}
                    className="flex-1"
                  />
                  <button
                    type="button"
                    className={`${secondaryButton} mt-[27px]`}
                    onClick={() =>
                      setItems((current) =>
                        current.map((item) => ({
                          ...item,
                          taxRate: everyRate,
                        })),
                      )
                    }
                    disabled={busy}
                  >
                    Set every line
                  </button>
                </div>
                {!creditNote && (
                  <Field
                    label="Currency"
                    as="select"
                    value={currency}
                    onChange={(event) => setCurrency(event.target.value)}
                    disabled={busy}
                  >
                    {currencies.map((item) => (
                      <option key={item.code} value={item.code}>
                        {item.code} — {item.currency}
                      </option>
                    ))}
                  </Field>
                )}
                {foreign && !creditNote && (
                  <Field
                    label={`Exchange rate (1 ${currency} in ${base})`}
                    type="number"
                    min="0.0000000001"
                    step="0.0000000001"
                    value={exchangeRate}
                    onChange={(event) => setExchangeRate(event.target.value)}
                    className="col-start-2 max-[700px]:col-start-auto"
                    hint={
                      gstMode
                        ? "Needed to issue an invoice in GST mode. It is fixed once issued."
                        : doc?.exchangeRate && doc.exchangeBase !== base
                          ? `Optional. The rate saved was to ${doc.exchangeBase}, which you no longer report in.`
                          : `Optional. Reports can then show this document in ${base}. It is fixed once issued.`
                    }
                    disabled={busy}
                  />
                )}
                {gstMode && (
                  <>
                    <Field
                      label="Place of supply"
                      as="select"
                      value={placeChoice}
                      onChange={(event) => setPlaceChoice(event.target.value)}
                      hint={
                        placeChoice
                          ? "Chosen by you."
                          : customer?.gstin
                            ? "Worked out from the client’s GSTIN."
                            : customer?.stateCode
                              ? "Worked out from the client’s state."
                              : "Your own state, as the client has none."
                      }
                      disabled={busy}
                    >
                      <option value="">
                        {workedOut
                          ? `Worked out: ${placeName(workedOut)} (${workedOut})`
                          : "Worked out once your GSTIN is added"}
                      </option>
                      {gstStates.map((state) => (
                        <option key={state.code} value={state.code}>
                          {state.code} – {state.name}
                        </option>
                      ))}
                    </Field>
                    <div className="flex min-w-0 flex-col gap-4 pt-[27px] max-[700px]:pt-0">
                      {place === "96" ? (
                        <fieldset className="m-0 flex flex-col gap-2.5 border-0 p-0">
                          <legend className="mb-2 text-[13px] text-spot-body">
                            Export
                          </legend>
                          {[
                            [
                              "LUT",
                              "Under your LUT, without IGST",
                              settings?.lutReference
                                ? `Lines are issued at 0%. LUT ${settings.lutReference}.`
                                : "Add your LUT reference in Settings first.",
                            ],
                            [
                              "IGST",
                              "With IGST paid",
                              "IGST at each line’s rate.",
                            ],
                          ].map(([value, label, note]) => (
                            <label
                              key={value}
                              className="flex items-start gap-3 text-[14px] text-spot-ink"
                            >
                              <input
                                type="radio"
                                name="export-type"
                                className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
                                checked={exportType === value}
                                onChange={() => setExportType(value)}
                                disabled={
                                  busy ||
                                  (value === "LUT" && !settings?.lutReference)
                                }
                              />
                              <span>
                                {label}
                                <span className="block text-[13px] text-spot-muted">
                                  {note}
                                </span>
                              </span>
                            </label>
                          ))}
                        </fieldset>
                      ) : (
                        <label className="flex items-start gap-3 text-[14px] text-spot-ink">
                          <input
                            type="checkbox"
                            className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
                            checked={reverseCharge}
                            onChange={(event) =>
                              setReverseCharge(event.target.checked)
                            }
                            disabled={busy}
                          />
                          <span>
                            Reverse charge
                            <span className="block text-[13px] text-spot-muted">
                              The recipient pays the GST, so it isn’t billed.
                            </span>
                          </span>
                        </label>
                      )}
                    </div>
                  </>
                )}
              </div>
            </Panel>
            <Panel title="Notes & terms">
              <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
                <Field
                  label="Additional info"
                  maxLength={5000}
                  as="textarea"
                  rows="5"
                  value={additionalInfo}
                  onChange={(event) => setAdditionalInfo(event.target.value)}
                  disabled={busy}
                  placeholder="Payment information or a note for your customer"
                />
                <Field
                  label="Terms & conditions"
                  maxLength={5000}
                  as="textarea"
                  rows="5"
                  value={termsConditions}
                  onChange={(event) => setTermsConditions(event.target.value)}
                  disabled={busy}
                  placeholder="Your payment terms or conditions"
                />
              </div>
              {documentType === "Invoice" && (
                <label className="mt-5 flex items-start gap-3 text-[14px] text-spot-ink">
                  <input
                    type="checkbox"
                    className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
                    checked={showPaymentDetails}
                    onChange={(event) =>
                      setShowPaymentDetails(event.target.checked)
                    }
                    disabled={busy}
                  />
                  <span>
                    Show how to pay
                    <span className="block text-[13px] text-spot-muted">
                      Your details from Settings → Getting paid, while the
                      invoice is unpaid.
                    </span>
                  </span>
                </label>
              )}
            </Panel>
          </div>
          <aside className="flex min-w-0 flex-col gap-6 compact:gap-5 min-[1360px]:sticky min-[1360px]:top-[30px]">
            <Panel title="Cost summary">
              <dl className="m-0">
                {[
                  ["Subtotal", totals.subTotal],
                  ...taxRows(totals.groups, taxInfo, taxLabel),
                  [creditNote ? "Total credit" : "Total", totals.total],
                ].map(([label, value], index, rows) => {
                  const total = index === rows.length - 1;
                  return (
                    <div
                      key={label}
                      className={`flex items-baseline justify-between gap-5 ${total ? "mt-2 border-t border-[#23273514] pt-5" : "py-2.5"}`}
                    >
                      <dt className="text-[13.5px] text-spot-body">{label}</dt>
                      <dd className="text-right text-spot-ink [overflow-wrap:anywhere]">
                        <Amount
                          value={value}
                          currency={currency}
                          className={
                            total
                              ? "text-[34px] font-extralight tracking-[-0.05em]"
                              : "text-[17px] font-light tracking-[-0.03em]"
                          }
                        />
                      </dd>
                    </div>
                  );
                })}
              </dl>
              {creditNote && (
                <p
                  className={`mt-4 text-[13px] ${overCredit ? "text-overdue-strong" : "text-spot-muted"}`}
                  role={overCredit ? "alert" : undefined}
                >
                  {overCredit
                    ? `That is more than the ${money(credited.creditable, currency)} left to credit on ${credited.documentNumber}.`
                    : `Up to ${money(credited.creditable, currency)} can still be credited.`}
                </p>
              )}
            </Panel>
            <div className="flex flex-wrap gap-2.5">
              <button
                type="submit"
                value="draft"
                className={`${secondaryButton} flex-1`}
                disabled={busy}
              >
                <FiFileText aria-hidden="true" /> Save draft
              </button>
              <button
                type="submit"
                value="issue"
                className={`${primaryButton} flex-1`}
                disabled={busy || overCredit}
              >
                <FiCheck aria-hidden="true" />
                {busy ? "Saving…" : "Save and issue"}
              </button>
            </div>
          </aside>
        </div>
      </form>
      <NewClientDialog
        open={addingClient}
        onClose={() => setAddingClient(false)}
        onCreated={setCustomer}
      />
    </>
  );
};
const DocCreateEditForm = () => {
  const { id } = useParams();
  const [params] = useSearchParams();
  const clientId = id ? null : params.get("client");
  const navigate = useNavigate();
  useTitle(`${id ? "Edit document" : "Create document"} — Invoice Spot`);
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  const query = useGetSingleDocQuery(id, { skip: !id });
  const clients = useGetWorkspaceCustomersQuery();
  const settings = useGetSettingsQuery();
  const [create, { isLoading: creating }] = useCreateDocMutation();
  const [update, { isLoading: updating }] = useUpdateDocMutation();
  const [issueDoc, { isLoading: issuing }] = useIssueDocMutation();
  const doc = query.data?.document;
  // A draft saved and issued is changed first, then issued as saved.
  const save = async (payload, { issue }) => {
    try {
      let message;
      if (!id) {
        const result = await create({ ...payload, draft: !issue }).unwrap();
        message = issue
          ? result.message || "Your document was created successfully."
          : "Your draft was saved.";
      } else {
        const result = await update({ id, ...payload }).unwrap();
        message = issue
          ? (await issueDoc({ id }).unwrap()).message
          : "Your draft was saved.";
        if (!message) message = result.message;
      }
      toast.success(message || "Your document was saved.");
      navigate(
        doc?.documentType === "Credit Note"
          ? `/document/${id}`
          : clientId
            ? `/single-customer/${clientId}`
            : "/documents",
      );
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be saved."));
    }
  };
  return (
    <WorkspacePage section="Documents">
      <PageHeader
        back
        title={
          !id
            ? "Create document"
            : query.data?.document?.documentType === "Credit Note"
              ? "Edit credit note"
              : "Edit document"
        }
        description="The details of your work, presented with care."
      />
      {editRefusal ? (
        <EmptyState
          title="This business is view only for you."
          text={`${editRefusal}.`}
          icon={FiLock}
        />
      ) : (id && query.isLoading) || clients.isLoading || settings.isLoading ? (
        <LoadingState />
      ) : query.isError || clients.isError ? (
        <ErrorState
          error={query.error || clients.error}
          retry={() => {
            if (id) query.refetch();
            clients.refetch();
          }}
        />
      ) : isVoid(doc) ? (
        <EmptyState
          title="This document is void."
          text={
            doc.documentType === "Credit Note"
              ? "A void credit note can’t be changed. Make a new one from the document it credits."
              : "A void document can’t be changed. Duplicate it to start again."
          }
          icon={FiSlash}
          action={
            <Link className={secondaryButton} to={`/document/${id}`}>
              Back to the document
            </Link>
          }
        />
      ) : doc && !isDraft(doc) ? (
        <EmptyState
          title="This document is issued."
          text="It keeps its client, lines and amounts as it went out. Change its due date, notes or terms from its page, or void it and duplicate it."
          icon={FiLock}
          action={
            <Link className={secondaryButton} to={`/document/${id}`}>
              Back to the document
            </Link>
          }
        />
      ) : (
        (!id || query.data?.document) && (
          <DocumentFields
            key={id || `new-${clientId || ""}`}
            doc={query.data?.document}
            settings={settings.data?.settings}
            customers={clients.data?.myCustomers || []}
            initialCustomer={clients.data?.myCustomers?.find(
              (client) => client._id === clientId,
            )}
            onSubmit={save}
            busy={creating || updating || issuing}
          />
        )
      )}
    </WorkspacePage>
  );
};
export default DocCreateEditForm;
