import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import { Field, errorMessage } from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { useCreateCustomerMutation } from "../customersApiSlice";
import GstinField from "../../gst/GstinField";
import { isValidGstin, normalizeGstin } from "../../gst/gstin";

const FIELDS = [
  { key: "name", label: "Client name", required: true, maxLength: 255 },
  {
    key: "email",
    label: "Email address",
    required: true,
    type: "email",
    maxLength: 254,
  },
  {
    key: "phoneNumber",
    label: "Phone number",
    required: true,
    type: "tel",
    maxLength: 30,
  },
  { key: "vatTinNo", label: "VAT / TIN number", maxLength: 50 },
  { key: "gstin" },
  { key: "address", label: "Address", maxLength: 255, wide: true },
  { key: "city", label: "City", maxLength: 255 },
  { key: "country", label: "Country", maxLength: 255 },
];

const ClientForm = ({ busy, onSave, onClose }) => {
  const [values, setValues] = useState(
    Object.fromEntries(FIELDS.map(({ key }) => [key, ""])),
  );
  return (
    <form
      className="mt-5"
      onSubmit={(event) => {
        event.preventDefault();
        const gstin = normalizeGstin(values.gstin);
        if (gstin && !isValidGstin(gstin)) {
          toast.error("Check the GSTIN: it isn’t a valid one yet.");
          return;
        }
        onSave({ ...values, gstin });
      }}
    >
      <div className="grid grid-cols-2 gap-4 max-[700px]:grid-cols-1">
        {FIELDS.map(({ key, wide, ...props }, index) => {
          const Control = key === "gstin" ? GstinField : Field;
          return (
            <Control
              key={key}
              {...props}
              className={wide ? "col-span-full" : ""}
              value={values[key]}
              onChange={(event) =>
                setValues((current) => ({
                  ...current,
                  [key]: event.target.value,
                }))
              }
              disabled={busy}
              data-autofocus={index === 0 ? true : undefined}
            />
          );
        })}
      </div>
      <div className="mt-7 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button type="submit" className={primaryButton} disabled={busy}>
          <FiCheck aria-hidden="true" />
          {busy ? "Saving…" : "Add client"}
        </button>
      </div>
    </form>
  );
};

// Adds a client without leaving the page, then hands it to `onCreated`.
const NewClientDialog = ({ open, onClose, onCreated }) => {
  const [create, { isLoading }] = useCreateCustomerMutation();
  const save = async (values) => {
    try {
      const result = await create(values).unwrap();
      toast.success(
        `${result.createdCustomer.name} was added to your clients.`,
      );
      onCreated(result.createdCustomer);
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The client could not be added."));
    }
  };
  return (
    <Dialog
      open={open}
      title="New client"
      onClose={onClose}
      busy={isLoading}
      dismissible={false}
      size="medium"
    >
      {open && <ClientForm busy={isLoading} onSave={save} onClose={onClose} />}
    </Dialog>
  );
};

export default NewClientDialog;
