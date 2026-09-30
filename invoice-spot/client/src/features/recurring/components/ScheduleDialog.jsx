import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Field,
  dateInput,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  fieldLabel,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useCreateScheduleMutation,
  useUpdateScheduleMutation,
} from "../recurringApiSlice";
import { FREQUENCIES, afterOnePeriod } from "../frequencies";

const ENDS = [
  { value: "never", label: "Never" },
  { value: "date", label: "On a date" },
  { value: "count", label: "After a number of invoices" },
];

const initial = (schedule) => {
  const today = dateInput();
  if (!schedule) {
    return {
      frequency: "MONTHLY",
      startDate: afterOnePeriod(today, "MONTHLY"),
      ends: "never",
      endDate: "",
      maxRuns: "12",
      emailAutomatically: false,
    };
  }
  return {
    frequency: schedule.frequency,
    startDate:
      schedule.nextRun && schedule.nextRun >= today
        ? schedule.nextRun
        : afterOnePeriod(today, schedule.frequency),
    ends: schedule.endDate ? "date" : schedule.maxRuns ? "count" : "never",
    endDate: schedule.endDate || "",
    maxRuns: String(schedule.maxRuns || 12),
    emailAutomatically: schedule.emailAutomatically,
  };
};

const ScheduleForm = ({
  documentId,
  documentNumber,
  schedule,
  demo,
  onClose,
}) => {
  const [values, setValues] = useState(() => initial(schedule));
  const [create, { isLoading: creating }] = useCreateScheduleMutation();
  const [update, { isLoading: updating }] = useUpdateScheduleMutation();
  const busy = creating || updating;
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const every = FREQUENCIES.find(
    (frequency) => frequency.value === values.frequency,
  )?.every;
  const submit = async (event) => {
    event.preventDefault();
    const body = {
      frequency: values.frequency,
      startDate: values.startDate,
      endDate: values.ends === "date" ? values.endDate : null,
      maxRuns: values.ends === "count" ? Number(values.maxRuns) : null,
      emailAutomatically: values.emailAutomatically,
    };
    try {
      const result = schedule
        ? await update({ id: schedule._id, ...body }).unwrap()
        : await create({ documentId, ...body }).unwrap();
      toast.success(result.message || "The schedule was saved.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The schedule could not be saved."));
    }
  };
  return (
    <form className="mt-5 flex flex-col gap-5" onSubmit={submit}>
      <p className="text-[14px] leading-[1.6] text-spot-body">
        Each new invoice copies {documentNumber}, with its own number and a due
        date from your payment terms.
      </p>
      <div className="grid grid-cols-2 gap-4 max-[560px]:grid-cols-1">
        <Field
          label="Repeats"
          as="select"
          value={values.frequency}
          onChange={change("frequency")}
          disabled={busy}
          data-autofocus
        >
          {FREQUENCIES.map((frequency) => (
            <option key={frequency.value} value={frequency.value}>
              {frequency.label}
            </option>
          ))}
        </Field>
        <Field
          label="Next invoice on"
          type="date"
          required
          min={dateInput()}
          value={values.startDate}
          onChange={change("startDate")}
          disabled={busy}
          hint={`Then every ${every}.`}
        />
      </div>
      <fieldset className="m-0 flex min-w-0 flex-col gap-3 border-0 p-0">
        <legend className={`mb-2 ${fieldLabel}`}>Ends</legend>
        {ENDS.map((end) => (
          <label
            key={end.value}
            className="flex items-center gap-3 text-[14px] text-spot-ink"
          >
            <input
              type="radio"
              name="schedule-ends"
              className="size-[18px] shrink-0 accent-spot-violet"
              checked={values.ends === end.value}
              onChange={() =>
                setValues((current) => ({ ...current, ends: end.value }))
              }
              disabled={busy}
            />
            {end.label}
          </label>
        ))}
        {values.ends === "date" && (
          <Field
            label="Last invoice on or before"
            type="date"
            required
            min={values.startDate}
            value={values.endDate}
            onChange={change("endDate")}
            disabled={busy}
          />
        )}
        {values.ends === "count" && (
          <Field
            label="Number of invoices"
            type="number"
            required
            min="1"
            max="120"
            step="1"
            value={values.maxRuns}
            onChange={change("maxRuns")}
            disabled={busy}
            hint="Invoices already made count toward it."
          />
        )}
      </fieldset>
      <label className="flex items-start gap-3 text-[14px] text-spot-ink">
        <input
          type="checkbox"
          className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
          checked={values.emailAutomatically}
          onChange={(event) =>
            setValues((current) => ({
              ...current,
              emailAutomatically: event.target.checked,
            }))
          }
          disabled={busy || demo}
        />
        <span>
          Email each new invoice to the client
          <span className="mt-0.5 block text-[12.5px] text-spot-muted">
            {demo
              ? "Emailing is turned off for the shared demo account."
              : "With your usual email message and the PDF attached."}
          </span>
        </span>
      </label>
      <div className="mt-2 flex flex-wrap justify-end gap-2.5">
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
          {busy ? "Saving…" : schedule ? "Save schedule" : "Make recurring"}
        </button>
      </div>
    </form>
  );
};

// Makes an invoice repeat, or changes how it repeats.
const ScheduleDialog = ({
  open,
  onClose,
  documentId,
  documentNumber,
  schedule,
  demo = false,
}) => (
  <Dialog
    open={open}
    title={schedule ? "Change how it repeats" : "Make recurring"}
    onClose={onClose}
    dismissible={false}
    size="medium"
  >
    {open && (
      <ScheduleForm
        key={schedule?._id || "new"}
        documentId={documentId}
        documentNumber={documentNumber}
        schedule={schedule}
        demo={demo}
        onClose={onClose}
      />
    )}
  </Dialog>
);

export default ScheduleDialog;
