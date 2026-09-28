import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { FiCheck, FiPlus, FiRepeat, FiTrash2 } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import currencies from "../../../world_currencies.json";
import {
  useCreateDocMutation,
  useGetSingleDocQuery,
  useUpdateDocMutation,
} from "../documentsApiSlice";
import { useGetWorkspaceCustomersQuery } from "../../workspace/workspaceApiSlice";
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

const blankItem = () => ({
  itemName: "",
  unitPrice: "",
  quantity: "",
  discount: "",
});
const DocumentFields = ({ doc, customers, onSubmit, busy }) => {
  const navigate = useNavigate();
  const [customer, setCustomer] = useState(doc?.customer || null);
  const [documentType, setDocumentType] = useState(
    doc?.documentType || "Invoice",
  );
  const [currency, setCurrency] = useState(doc?.currency || currencies[0].code);
  const [dueDate, setDueDate] = useState(
    dateInput(doc?.dueDate || Date.now() + 7 * 86400000),
  );
  const [rates, setRates] = useState(doc?.rates ?? "0");
  const [items, setItems] = useState(
    doc?.billingItems?.map((item) => ({ ...item })) || [blankItem()],
  );
  const [additionalInfo, setAdditionalInfo] = useState(
    doc?.additionalInfo || "",
  );
  const [termsConditions, setTermsConditions] = useState(
    doc?.termsConditions || "",
  );
  const totals = documentTotals(items, rates);
  const status =
    documentType === "Receipt" ? "Paid" : doc?.status || "Not Paid";
  const changeItem = (index, key, value) =>
    setItems((current) =>
      current.map((item, i) =>
        i === index ? { ...item, [key]: value } : item,
      ),
    );
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (!customer) {
          toast.error("Select a customer before saving the document.");
          return;
        }
        onSubmit(
          documentPayload({
            doc,
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
                        <FiRepeat aria-hidden="true" /> Choose another customer
                      </button>
                    </div>
                  ) : (
                    <div className="flex flex-col items-start gap-3">
                      <Field
                        label="Select a customer"
                        as="select"
                        required
                        value=""
                        onChange={(event) =>
                          setCustomer(
                            customers.find(
                              (client) => client._id === event.target.value,
                            ),
                          )
                        }
                        disabled={busy}
                        className="w-full"
                      >
                        <option value="">Choose a customer</option>
                        {customers.map((client) => (
                          <option key={client._id} value={client._id}>
                            {client.name}
                          </option>
                        ))}
                      </Field>
                      <button
                        type="button"
                        className={rowAction}
                        onClick={() => navigate("/create-customer")}
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
            <div className="[container-name:items] [container-type:inline-size]">
              {items.map((item, index) => (
                <div
                  className="grid grid-cols-[minmax(160px,2.4fr)_repeat(3,minmax(72px,1fr))_minmax(110px,1.1fr)_auto] items-end gap-3 border-t border-[#23273512] py-4 first:border-t-0 first:pt-0 items-md:grid-cols-3 items-sm:grid-cols-2"
                  key={index}
                >
                  <Field
                    label={`Product / service ${index + 1}`}
                    value={item.itemName || ""}
                    onChange={(event) =>
                      changeItem(index, "itemName", event.target.value)
                    }
                    disabled={busy}
                    className="items-md:col-span-full"
                  />
                  <Field
                    label={`Quantity ${index + 1}`}
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
                  <button
                    type="button"
                    className={`${removeItemButton} items-md:col-start-3 items-md:justify-self-end items-sm:col-start-2`}
                    aria-label={`Remove item ${index + 1}`}
                    onClick={() =>
                      setItems((current) =>
                        current.filter((_item, i) => i !== index),
                      )
                    }
                    disabled={busy}
                  >
                    <FiTrash2 aria-hidden="true" />
                  </button>
                </div>
              ))}
            </div>
            <div className="mt-2 border-t border-[#23273512] pt-4">
              <button
                type="button"
                className={rowAction}
                onClick={() => setItems((current) => [blankItem(), ...current])}
                disabled={busy}
              >
                <FiPlus aria-hidden="true" /> Add product or service
              </button>
            </div>
          </Panel>
          <Panel title="Tax & currency">
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="VAT / sales tax (%)"
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
                as="textarea"
                rows="5"
                value={additionalInfo}
                onChange={(event) => setAdditionalInfo(event.target.value)}
                disabled={busy}
                placeholder="Payment information or a note for your customer"
              />
              <Field
                label="Terms & conditions"
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
                ["VAT / sales tax", totals.salesTax],
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
  );
};
const DocCreateEditForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  useTitle(`${id ? "Edit document" : "Create document"} — Invoice Spot`);
  const query = useGetSingleDocQuery(id, { skip: !id });
  const clients = useGetWorkspaceCustomersQuery();
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
      navigate("/documents");
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
      {(id && query.isLoading) || clients.isLoading ? (
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
            key={id || "new"}
            doc={query.data?.document}
            customers={clients.data?.myCustomers || []}
            onSubmit={save}
            busy={creating || updating}
          />
        )
      )}
    </WorkspacePage>
  );
};
export default DocCreateEditForm;
