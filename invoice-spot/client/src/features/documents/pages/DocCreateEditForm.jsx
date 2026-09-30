import { useEffect, useId, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  FiBookmark,
  FiCheck,
  FiPlus,
  FiRepeat,
  FiTrash2,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import currencies from "../../../world_currencies.json";
import {
  useCreateDocMutation,
  useGetSingleDocQuery,
  useUpdateDocMutation,
} from "../documentsApiSlice";
import { useGetWorkspaceCustomersQuery } from "../../workspace/workspaceApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import NewClientDialog from "../../customers/components/NewClientDialog";
import { matchClients } from "./documentEditorModel";
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
  StatusBadge,
  Amount,
  dateInput,
  dateInstant,
  dateLabel,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  panelNote,
  primaryButton,
  rowAction,
} from "../../../components/workspace/pastel";
import {
  documentPayload,
  documentTotals,
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

const blankItem = () => ({
  itemName: "",
  unitPrice: "",
  quantity: "",
  discount: "",
});
// A new document starts from the business settings, and from a client when
// it was started from their page; an existing one keeps its own values.
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
  const [dueDate, setDueDate] = useState(
    dateInput(
      doc?.dueDate || Date.now() + (settings?.paymentTermsDays ?? 7) * 86400000,
    ),
  );
  const [rates, setRates] = useState(
    doc?.rates ?? String(settings?.defaultTaxRate ?? "0"),
  );
  const [items, setItems] = useState(
    doc?.billingItems?.map((item) => ({ ...item })) || [blankItem()],
  );
  const [additionalInfo, setAdditionalInfo] = useState(
    (doc ? doc.additionalInfo : settings?.defaultNotes) || "",
  );
  const [termsConditions, setTermsConditions] = useState(
    (doc ? doc.termsConditions : settings?.defaultTerms) || "",
  );
  const taxLabel = settings?.taxLabel || "Tax";
  const totals = documentTotals(items, rates);
  const catalogListId = useId();
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
  // Picking a saved item fills in its price and a quantity of one, unless
  // the line already has them.
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
        };
      }),
    );
  const saveToCatalog = async (item) => {
    try {
      await createItem({
        name: item.itemName.trim(),
        unitPrice: Number(item.unitPrice) || 0,
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
          onSubmit(
            documentPayload({
              customer,
              documentType,
              dueDate: dateInstant(dueDate),
              rates,
              currency,
              items,
              additionalInfo,
              termsConditions,
            }),
          );
        }}
      >
        <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
          <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
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
                                  <span className="text-[12.5px] text-spot-muted [overflow-wrap:anywhere]">
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
                    label="Due date"
                    type="date"
                    required
                    value={dueDate}
                    onChange={(event) => setDueDate(event.target.value)}
                    disabled={busy}
                  />
                  <dl className="grid gap-4">
                    <div>
                      <dt className={panelNote}>Payment status</dt>
                      <dd className="mt-1.5">
                        <StatusBadge status={status} />
                      </dd>
                    </div>
                    <div>
                      <dt className={panelNote}>Date of issue</dt>
                      <dd className="mt-1 text-[15px] text-spot-ink">
                        {dateLabel(doc?.createdAt || new Date())}
                      </dd>
                    </div>
                  </dl>
                </div>
              </div>
            </Panel>
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
              <div className="[container-name:items] [container-type:inline-size]">
                {items.map((item, index) => (
                  <div
                    className="grid grid-cols-[minmax(160px,2.4fr)_repeat(3,minmax(72px,1fr))_minmax(110px,1.1fr)_96px] items-end gap-3 border-t border-[#23273512] py-4 first:border-t-0 first:pt-0 items-md:grid-cols-3 items-sm:grid-cols-2"
                    key={index}
                  >
                    <Field
                      label={`Product / service ${index + 1}`}
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
                    <Field
                      label={`Quantity ${index + 1}`}
                      max="1000000"
                      type="number"
                      min="0"
                      step="1"
                      value={item.quantity ?? ""}
                      onChange={(event) =>
                        changeItem(index, "quantity", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={`Unit price ${index + 1}`}
                      type="number"
                      min="0"
                      step="any"
                      value={item.unitPrice ?? ""}
                      onChange={(event) =>
                        changeItem(index, "unitPrice", event.target.value)
                      }
                      disabled={busy}
                    />
                    <Field
                      label={`Discount (%) ${index + 1}`}
                      type="number"
                      min="0"
                      max="100"
                      step="any"
                      value={item.discount ?? ""}
                      onChange={(event) =>
                        changeItem(index, "discount", event.target.value)
                      }
                      disabled={busy}
                    />
                    <div className="flex min-w-0 flex-col gap-2">
                      <span className={fieldLabel}>Line total</span>
                      <strong className="flex h-11 items-center justify-end font-normal text-spot-ink items-md:justify-start">
                        <Amount
                          value={lineAmount(item)}
                          className="text-[17px] font-light tracking-[-0.03em]"
                        />
                      </strong>
                    </div>
                    <div className="flex justify-end gap-2 items-md:col-start-3 items-md:justify-self-end items-sm:col-start-2">
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
                    setItems((current) => [...current, blankItem()]);
                    setFocusLine(items.length);
                  }}
                  disabled={busy}
                >
                  <FiPlus aria-hidden="true" /> Add product or service
                </button>
              </div>
            </Panel>
            <Panel title="Tax & currency">
              <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
                <Field
                  label={`${taxLabel} rate (%)`}
                  max="100"
                  type="number"
                  min="0"
                  step="any"
                  value={rates}
                  onChange={(event) => setRates(event.target.value)}
                  disabled={busy}
                />
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
            </Panel>
          </div>
          <aside className="flex min-w-0 flex-col gap-6 compact:gap-5 min-[1360px]:sticky min-[1360px]:top-[30px]">
            <Panel title="Cost summary">
              <dl className="m-0">
                {[
                  ["Subtotal", totals.subTotal],
                  [taxLabel, totals.salesTax],
                  ["Total", totals.total],
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
            </Panel>
            <div className="flex">
              <button
                type="submit"
                className={`${primaryButton} flex-1`}
                disabled={busy}
              >
                <FiCheck aria-hidden="true" />
                {busy ? "Saving…" : doc ? "Save document" : "Create document"}
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
  const query = useGetSingleDocQuery(id, { skip: !id });
  const clients = useGetWorkspaceCustomersQuery();
  const settings = useGetSettingsQuery();
  const [create, { isLoading: creating }] = useCreateDocMutation();
  const [update, { isLoading: updating }] = useUpdateDocMutation();
  const save = async (payload) => {
    try {
      const result = id
        ? await update({ id, ...payload }).unwrap()
        : await create(payload).unwrap();
      toast.success(
        result.message || "Your document was created successfully.",
      );
      navigate(clientId ? `/single-customer/${clientId}` : "/documents");
    } catch (error) {
      toast.error(errorMessage(error, "The document could not be saved."));
    }
  };
  return (
    <WorkspacePage section="Documents">
      <PageHeader
        back
        title={id ? "Edit document" : "Create document"}
        description="The details of your work, presented with care."
      />
      {(id && query.isLoading) || clients.isLoading || settings.isLoading ? (
        <LoadingState />
      ) : query.isError || clients.isError ? (
        <ErrorState
          error={query.error || clients.error}
          retry={() => {
            if (id) query.refetch();
            clients.refetch();
          }}
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
            busy={creating || updating}
          />
        )
      )}
    </WorkspacePage>
  );
};
export default DocCreateEditForm;
