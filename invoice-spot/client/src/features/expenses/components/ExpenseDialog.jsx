import { useId, useState } from "react";
import { FiCheck } from "react-icons/fi";
import { toast } from "react-toastify";
import currencies from "../../../world_currencies.json";
import { Dialog } from "../../../components/workspace/Dialog";
import {
  Field,
  dateInput,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import { PAYMENT_METHODS } from "../../documents/paymentMethods";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import useBusinessToday from "../../business/useBusinessToday";
import {
  useCreateExpenseMutation,
  useGetExpenseCategoriesQuery,
  useUpdateExpenseMutation,
} from "../expensesApiSlice";
import { expenseForm, expensePayload, taxError } from "../expenseModel";

const NO_CATEGORIES = [];

const ExpenseForm = ({ expense, settings, busy, onSave, onClose }) => {
  const base = settings.baseCurrency || settings.defaultCurrency;
  // A new expense is dated today by the business's calendar, the one the
  // server counts its days by, and none later.
  const today = dateInput(useBusinessToday());
  const [values, setValues] = useState(() =>
    expenseForm(expense, {
      today,
      currency: settings.defaultCurrency,
      base,
    }),
  );
  // The categories already in use, offered as the category is typed.
  const categories =
    useGetExpenseCategoriesQuery().data?.categories || NO_CATEGORIES;
  const categoryList = useId();
  const change = (key) => (event) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));
  const foreign = values.currency !== base;
  const taxProblem = taxError(values);
  return (
    <form
      className="mt-5 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave(expensePayload(values, base));
      }}
    >
      <div className="grid grid-cols-2 gap-4 max-[480px]:grid-cols-1">
        <Field
          label="Date paid"
          type="date"
          required
          max={today}
          value={values.date}
          onChange={change("date")}
          disabled={busy}
          data-autofocus
        />
        <Field
          label="Category"
          required
          maxLength={60}
          list={categoryList}
          value={values.category}
          onChange={change("category")}
          disabled={busy}
          hint="Such as Travel, Rent or Software."
        />
      </div>
      <datalist id={categoryList}>
        {categories.map((category) => (
          <option key={category} value={category} />
        ))}
      </datalist>
      <Field
        label="Vendor"
        maxLength={255}
        value={values.vendor}
        onChange={change("vendor")}
        disabled={busy}
        hint="Who you paid."
      />
      <div className="grid grid-cols-2 gap-4 max-[480px]:grid-cols-1">
        <Field
          label="Total paid"
          type="number"
          min="0.01"
          step="0.01"
          required
          value={values.total}
          onChange={change("total")}
          disabled={busy}
          hint="What the bill came to, tax included."
        />
        <Field
          label="Tax paid"
          type="number"
          min="0"
          step="0.01"
          value={values.tax}
          onChange={change("tax")}
          disabled={busy}
          error={taxProblem}
          hint="The part of the total that was tax, such as GST."
        />
      </div>
      <label className="flex items-start gap-3 text-[14px] text-spot-ink">
        <input
          type="checkbox"
          className="mt-0.5 size-[18px] shrink-0 accent-spot-violet"
          checked={values.taxReclaimable}
          onChange={(event) =>
            setValues((current) => ({
              ...current,
              taxReclaimable: event.target.checked,
            }))
          }
          disabled={busy}
        />
        <span>
          This tax can be claimed back
          <span className="block text-[13px] text-spot-muted">
            Such as GST input tax credit. Tax you claim back isn’t counted as a
            cost.
          </span>
        </span>
      </label>
      <div className="grid grid-cols-2 gap-4 max-[480px]:grid-cols-1">
        <Field
          label="Currency"
          as="select"
          value={values.currency}
          onChange={change("currency")}
          disabled={busy}
        >
          {currencies.map((item) => (
            <option key={item.code} value={item.code}>
              {item.code} — {item.currency}
            </option>
          ))}
        </Field>
        {foreign && (
          <Field
            label={`Exchange rate (1 ${values.currency} in ${base})`}
            type="number"
            min="0.0000000001"
            step="0.0000000001"
            value={values.exchangeRate}
            onChange={change("exchangeRate")}
            disabled={busy}
            hint={`Optional. The rate to ${base}, your reporting currency.`}
          />
        )}
        <Field
          label="Payment method"
          as="select"
          value={values.paymentMethod}
          onChange={change("paymentMethod")}
          disabled={busy}
        >
          <option value="">Not recorded</option>
          {PAYMENT_METHODS.map((method) => (
            <option key={method}>{method}</option>
          ))}
        </Field>
        <Field
          label="Reference"
          maxLength={100}
          value={values.reference}
          onChange={change("reference")}
          disabled={busy}
          hint="A bill or transaction number."
        />
      </div>
      <Field
        label="Notes"
        as="textarea"
        maxLength={5000}
        value={values.notes}
        onChange={change("notes")}
        disabled={busy}
      />
      <div className="mt-3 flex flex-wrap justify-end gap-2.5">
        <button
          type="button"
          className={secondaryButton}
          onClick={onClose}
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="submit"
          className={primaryButton}
          disabled={busy || Boolean(taxProblem)}
        >
          <FiCheck aria-hidden="true" />
          {busy ? "Saving…" : "Save expense"}
        </button>
      </div>
    </form>
  );
};

// Records an expense, or changes one when `expense` is given.
const ExpenseDialog = ({ open, expense, onClose }) => {
  const settings = useGetSettingsQuery().data?.settings;
  const [create, { isLoading: creating }] = useCreateExpenseMutation();
  const [update, { isLoading: updating }] = useUpdateExpenseMutation();
  const busy = creating || updating;
  const save = async (values) => {
    try {
      const result = expense
        ? await update({ id: expense._id, ...values }).unwrap()
        : await create(values).unwrap();
      toast.success(result.message || "Expense saved.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The expense could not be saved."));
    }
  };
  return (
    <Dialog
      open={open}
      title={expense ? "Edit expense" : "New expense"}
      onClose={onClose}
      busy={busy}
      dismissible={false}
    >
      {open && settings && (
        <ExpenseForm
          key={expense?._id || "new"}
          expense={expense}
          settings={settings}
          busy={busy}
          onSave={save}
          onClose={onClose}
        />
      )}
    </Dialog>
  );
};

export default ExpenseDialog;
