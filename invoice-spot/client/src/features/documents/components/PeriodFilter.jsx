import { Select } from "../../../components/workspace/Select";
import { PERIODS } from "../documentFilters";

// The Documents list's periods, as a toolbar menu. `label` names what the
// period is of, for screen readers, such as the days documents were issued.
const PeriodFilter = ({ value, onChange, label }) => (
  <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
    <span className="sr-only">{label}</span>
    <Select
      size="toolbar"
      className="max-[700px]:w-full"
      value={value}
      onChange={onChange}
    >
      {PERIODS.map((period) => (
        <option key={period.value} value={period.value}>
          {period.label}
        </option>
      ))}
    </Select>
  </label>
);

export default PeriodFilter;
