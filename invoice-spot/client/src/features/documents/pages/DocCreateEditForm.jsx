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
  Badge,
  statusTone,
  dateInput,
  dateInstant,
  dateLabel,
  money,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  documentPayload,
  documentTotals,
  lineAmount,
} from "./documentEditorModel";

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
      <div className="workspace-form-layout">
        <div className="workspace-form-main">
          <Panel
            title="Document details"
            description="Choose the document and who it’s for."
          >
            <div className="workspace-doc-form-top">
              <div>
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
                <div className="workspace-customer-snapshot">
                  {customer ? (
                    <>
                      <strong>{customer.name}</strong>
                      <p>{customer.email}</p>
                      <p>Account no. {customer.accountNo || "—"}</p>
                      <p>VAT / TIN: {customer.vatTinNo || "—"}</p>
                      <p>{customer.phoneNumber}</p>
                      <p>{customer.address}</p>
                      <button
                        type="button"
                        className="workspace-row-action"
                        onClick={() => setCustomer(null)}
                        disabled={busy}
                      >
                        <FiRepeat aria-hidden="true" /> Choose another customer
                      </button>
                    </>
                  ) : (
                    <>
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
                        className="workspace-row-action"
                        onClick={() => navigate("/create-customer")}
                      >
                        <FiPlus aria-hidden="true" /> Add new customer
                      </button>
                    </>
                  )}
                </div>
              </div>
              <div>
                <Field
                  label="Due date"
                  type="date"
                  required
                  value={dueDate}
                  onChange={(event) => setDueDate(event.target.value)}
                  disabled={busy}
                />
                <div className="workspace-customer-snapshot">
                  <p>Payment status</p>
                  <Badge tone={statusTone(status)}>{status}</Badge>
                  <p>
                    Date of issue: {dateLabel(doc?.createdAt || new Date())}
                  </p>
                  <p>
                    Total amount:{" "}
                    <strong>{money(totals.total, currency)}</strong>
                  </p>
                </div>
              </div>
            </div>
          </Panel>
          <Panel
            title="Products & services"
            description="The details behind the total."
          >
            <div className="workspace-line-items">
              {items.map((item, index) => (
                <div className="workspace-line-item" key={index}>
                  <Field
                    label={`Product / service ${index + 1}`}
                    value={item.itemName || ""}
                    onChange={(event) =>
                      changeItem(index, "itemName", event.target.value)
                    }
                    disabled={busy}
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
                  <div className="workspace-line-total">
                    <span>Line total</span>
                    <strong>{money(lineAmount(item))}</strong>
                  </div>
                  <button
                    type="button"
                    className="workspace-row-action is-danger"
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
            <div className="workspace-add-item">
              <button
                type="button"
                className="workspace-row-action"
                onClick={() => setItems((current) => [blankItem(), ...current])}
                disabled={busy}
              >
                <FiPlus aria-hidden="true" /> Add product or service
              </button>
            </div>
          </Panel>
          <Panel title="Tax & currency">
            <div className="workspace-form-grid">
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
            <div className="workspace-form-grid">
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
        <aside className="workspace-form-aside">
          <Panel title="Cost summary">
            <dl className="workspace-summary-list">
              <div>
                <dt>Subtotal</dt>
                <dd>{money(totals.subTotal, currency)}</dd>
              </div>
              <div>
                <dt>VAT / sales tax</dt>
                <dd>{money(totals.salesTax, currency)}</dd>
              </div>
              <div>
                <dt>Total</dt>
                <dd>{money(totals.total, currency)}</dd>
              </div>
            </dl>
          </Panel>
          <div className="workspace-form-actions">
            <button type="submit" className="workspace-button" disabled={busy}>
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
