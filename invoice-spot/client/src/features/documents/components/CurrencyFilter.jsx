import { toolbarSelect } from "../../../components/workspace/pastel";

// The currency a list is narrowed to, offered when the business bills in
// more than one. A currency the address names is always shown, even while
// the business's currencies load or when they couldn't be, so a filter in
// use never goes out of sight.
const CurrencyFilter = ({ value, currencies = [], onChange }) => {
  if (currencies.length < 2 && !value) return null;
  const options =
    currencies.includes(value) || !value ? currencies : [...currencies, value];
  return (
    <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
      <span className="sr-only">Currency</span>
      <select
        className={`${toolbarSelect} max-[700px]:w-full`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">All currencies</option>
        {options.map((currency) => (
          <option key={currency} value={currency}>
            {currency}
          </option>
        ))}
      </select>
    </label>
  );
};

export default CurrencyFilter;
