import { useState } from "react";
import { FiBriefcase, FiCheck } from "react-icons/fi";
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
  useGetBusinessQuery,
  useUpdateBusinessMutation,
} from "../../business/businessApiSlice";
import { useUploadProfileLogoMutation } from "../../workspace/workspaceApiSlice";
import SettingsTabs from "../components/SettingsTabs";
import { printsOnPdfs } from "../logo";
import { useBusiness } from "../../business/permissions";

const KEYS = [
  "name",
  "legalName",
  "taxId",
  "email",
  "phoneNumber",
  "address",
  "city",
  "state",
  "postalCode",
  "country",
  "logo",
];
const MAX_LOGO_BYTES = 2 * 1024 * 1024;

const BusinessFields = ({ business }) => {
  const [values, setValues] = useState(() =>
    Object.fromEntries(KEYS.map((key) => [key, business[key] || ""])),
  );
  const [save, { isLoading: saving }] = useUpdateBusinessMutation();
  const [upload, { isLoading: uploading }] = useUploadProfileLogoMutation();
  const refusal = useBusiness().reason("MANAGE_BUSINESS");
  const busy = saving || uploading || Boolean(refusal);
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const field = (key) => ({
    name: key,
    value: values[key],
    onChange: change(key),
    disabled: busy,
  });
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await save(values).unwrap();
      toast.success(result.message || "Your business details were saved.");
    } catch (error) {
      toast.error(
        errorMessage(error, "Your business details could not be saved."),
      );
    }
  };
  const uploadLogo = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > MAX_LOGO_BYTES) {
      toast.error("Logos can be at most 2 MB.");
      event.target.value = "";
      return;
    }
    const body = new FormData();
    body.append("logo", file);
    try {
      const url = await upload(body).unwrap();
      setValues((current) => ({ ...current, logo: url }));
      toast.success("Logo uploaded. Save your changes to use it.");
    } catch (error) {
      toast.error(errorMessage(error, "Your logo could not be uploaded."));
    }
    event.target.value = "";
  };
  return (
    <form onSubmit={submit}>
      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
        <div className="flex min-w-0 flex-col gap-6 compact:gap-5">
          {refusal && <p className={panelNote}>{refusal}.</p>}
          <Panel
            title="Business details"
            description="How your business appears on documents, share links and emails."
          >
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="Business name"
                maxLength={255}
                required
                autoComplete="organization"
                className="col-span-full"
                {...field("name")}
              />
              <Field
                label="Legal name"
                maxLength={255}
                hint="If it differs from the name you trade under."
                {...field("legalName")}
              />
              <Field
                label="Tax ID"
                maxLength={50}
                hint="Such as a VAT number or GSTIN."
                {...field("taxId")}
              />
              <Field
                label="Email"
                type="email"
                maxLength={254}
                autoComplete="email"
                hint="Clients' replies to your emails come here."
                {...field("email")}
              />
              <Field
                label="Phone number"
                type="tel"
                maxLength={30}
                autoComplete="tel"
                hint="Include your country code."
                {...field("phoneNumber")}
              />
            </div>
          </Panel>
          <Panel title="Address">
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              <Field
                label="Address"
                maxLength={255}
                autoComplete="street-address"
                className="col-span-full"
                {...field("address")}
              />
              <Field
                label="City"
                maxLength={255}
                autoComplete="address-level2"
                {...field("city")}
              />
              <Field
                label="State or region"
                maxLength={255}
                autoComplete="address-level1"
                {...field("state")}
              />
              <Field
                label="Postal code"
                maxLength={30}
                autoComplete="postal-code"
                {...field("postalCode")}
              />
              <Field
                label="Country"
                maxLength={255}
                autoComplete="country-name"
                {...field("country")}
              />
            </div>
          </Panel>
          <Panel title="Logo">
            <div className="grid grid-cols-2 gap-5 max-[700px]:grid-cols-1">
              {values.logo && (
                <img
                  className="col-span-full max-h-16 max-w-[160px] object-contain"
                  src={values.logo}
                  alt={`${values.name || "Business"} logo`}
                />
              )}
              <Field
                label="Logo URL"
                maxLength={500}
                hint={
                  printsOnPdfs(values.logo)
                    ? undefined
                    : "Your PDFs leave this logo out. Upload it below as a PNG, JPEG or GIF to print it on them too."
                }
                className="col-span-full"
                {...field("logo")}
              />
              <Field
                label="Choose your logo"
                type="file"
                accept="image/png,image/jpeg,image/gif"
                hint="A PNG, JPEG or GIF image of up to 2 MB, so it prints on your PDFs too."
                onChange={uploadLogo}
                disabled={busy}
                className="col-span-full"
              />
              {uploading && (
                <p
                  role="status"
                  className="col-span-full text-[13px] text-spot-violet"
                >
                  Uploading your logo…
                </p>
              )}
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
            <FiBriefcase aria-hidden="true" />
            <h2 className={sectionTitle}>Your business, on every document.</h2>
            <p>
              Invoices, quotations, receipts and statements show these details,
              and so do the links you share with clients.
            </p>
          </div>
        </aside>
      </div>
    </form>
  );
};

const BusinessProfilePage = () => {
  useTitle("Business details — Invoice Spot");
  const query = useGetBusinessQuery();
  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="Your business details, as your documents show them."
        actions={<SettingsTabs />}
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        query.data?.business && (
          <BusinessFields
            key={query.data.business._id}
            business={query.data.business}
          />
        )
      )}
    </WorkspacePage>
  );
};

export default BusinessProfilePage;
