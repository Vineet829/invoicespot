import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { FiCheck, FiUsers } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useCreateCustomerMutation,
  useGetSingleCustomerQuery,
  useUpdateCustomerInfoMutation,
} from "../customersApiSlice";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  Field,
  LoadingState,
  ErrorState,
  errorMessage,
} from "../../../components/workspace/Workspace";

const fields = [
  {
    key: "name",
    label: "Customer name",
    required: true,
    autoComplete: "name",
    maxLength: 255,
  },
  {
    key: "email",
    label: "Email address",
    required: true,
    type: "email",
    autoComplete: "email",
    maxLength: 255,
  },
  {
    key: "phoneNumber",
    label: "Phone number",
    required: true,
    type: "tel",
    autoComplete: "tel",
    maxLength: 20,
    hint: "Include the country code, for example +1 555 010 0100.",
  },
  {
    key: "vatTinNo",
    label: "VAT / TIN number",
    type: "number",
    min: 0,
    step: 1,
  },
  {
    key: "address",
    label: "Address",
    autoComplete: "street-address",
    className: "workspace-span-full",
  },
  { key: "city", label: "City", autoComplete: "address-level2" },
  { key: "country", label: "Country", autoComplete: "country-name" },
];
const CustomerFields = ({ customer, onSubmit, busy }) => {
  const [values, setValues] = useState(() =>
    Object.fromEntries(fields.map(({ key }) => [key, customer?.[key] ?? ""])),
  );
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({ ...values, vatTinNo: Number(values.vatTinNo) || 0 });
      }}
    >
      <div className="workspace-form-layout">
        <div className="workspace-form-main">
          <Panel
            title="Client details"
            description="Contact and billing information"
          >
            <div className="workspace-form-grid">
              {fields.map(({ key, ...props }) => (
                <Field
                  key={key}
                  {...props}
                  name={key}
                  value={values[key]}
                  onChange={(event) =>
                    setValues((current) => ({
                      ...current,
                      [key]: event.target.value,
                    }))
                  }
                  disabled={busy}
                />
              ))}
            </div>
          </Panel>
          <div className="workspace-form-actions">
            <button type="submit" className="workspace-button" disabled={busy}>
              <FiCheck aria-hidden="true" />
              {busy ? "Saving…" : customer ? "Save changes" : "Create client"}
            </button>
          </div>
        </div>
        <aside className="workspace-form-aside">
          <div className="workspace-panel-body">
            <FiUsers aria-hidden="true" />
            <h2>Details that stay with you.</h2>
            <p>
              These details are used when you select this client for an invoice,
              quotation, or receipt.
            </p>
            <p>Fields marked with an asterisk are required.</p>
          </div>
        </aside>
      </div>
    </form>
  );
};
const CustomerForm = () => {
  const { custId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  useTitle(`${custId ? "Edit client" : "Add client"} — Invoice Spot`);
  const query = useGetSingleCustomerQuery(custId, { skip: !custId });
  const [create, { isLoading: creating }] = useCreateCustomerMutation();
  const [update, { isLoading: updating }] = useUpdateCustomerInfoMutation();
  const submit = async (values) => {
    try {
      const result = custId
        ? await update({ id: custId, ...values }).unwrap()
        : await create(values).unwrap();
      toast.success(result.message || "Client saved.");
      navigate(
        custId ? "/customers" : location.state?.from?.pathname || "/customers",
        { replace: !custId },
      );
    } catch (error) {
      toast.error(errorMessage(error, "The client could not be saved."));
    }
  };
  return (
    <WorkspacePage section="Clients">
      <PageHeader
        back
        title={custId ? "Edit client" : "Add client"}
        description={
          custId
            ? "Keep your client’s contact and billing details up to date."
            : "A few details now make the next invoice easier."
        }
      />
      {custId && query.isLoading ? (
        <LoadingState />
      ) : custId && query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        (!custId || query.data?.customer) && (
          <CustomerFields
            key={custId || "new"}
            customer={query.data?.customer}
            onSubmit={submit}
            busy={creating || updating}
          />
        )
      )}
    </WorkspacePage>
  );
};
export default CustomerForm;
